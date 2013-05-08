/*
 * Copyright (c) 2005 Sun Microsystems, Inc. All Rights Reserved.
 * Copyright (c) 2010 JogAmp Community. All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 * 
 * - Redistribution of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 * 
 * - Redistribution in binary form must reproduce the above copyright
 *   notice, this list of conditions and the following disclaimer in the
 *   documentation and/or other materials provided with the distribution.
 * 
 * Neither the name of Sun Microsystems, Inc. or the names of
 * contributors may be used to endorse or promote products derived from
 * this software without specific prior written permission.
 * 
 * This software is provided "AS IS," without a warranty of any kind. ALL
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES,
 * INCLUDING ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A
 * PARTICULAR PURPOSE OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. SUN
 * MICROSYSTEMS, INC. ("SUN") AND ITS LICENSORS SHALL NOT BE LIABLE FOR
 * ANY DAMAGES SUFFERED BY LICENSEE AS A RESULT OF USING, MODIFYING OR
 * DISTRIBUTING THIS SOFTWARE OR ITS DERIVATIVES. IN NO EVENT WILL SUN OR
 * ITS LICENSORS BE LIABLE FOR ANY LOST REVENUE, PROFIT OR DATA, OR FOR
 * DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL, INCIDENTAL OR PUNITIVE
 * DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY OF LIABILITY,
 * ARISING OUT OF THE USE OF OR INABILITY TO USE THIS SOFTWARE, EVEN IF
 * SUN HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 * 
 * You acknowledge that this software is not designed or intended for use
 * in the design, construction, operation or maintenance of any nuclear
 * facility.
 */

package com.jogamp.opengl.util.texture;

import java.nio.Buffer;
import java.nio.ByteBuffer;

import javax.media.opengl.GL;
import javax.media.opengl.GLContext;
import javax.media.opengl.GLProfile;

import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.util.GLBuffers;

/**
 * Represents the data for an OpenGL texture. This is separated from
 * the notion of a Texture to support things like streaming in of
 * textures in a background thread without requiring an OpenGL context
 * to be current on that thread.
 *
 * @author Chris Campbell
 * @author Kenneth Russell
 * @author Sven Gothel
 */

public class TextureData {
    /** ColorSpace of pixel data. */
    public static enum ColorSpace { RGB, YCbCr, YCCK, CMYK };   
    
    /** Pixel data attributes. */ 
    public static class PixelAttributes {
        /** Undefinded instance of {@link PixelAttributes}, having format:=0 and type:= 0. */ 
        public static final PixelAttributes UNDEF = new PixelAttributes(0, 0);
        
        /** The OpenGL pixel data format */
        public final int format;
        /** The OpenGL pixel data type  */
        public final int type;
        public PixelAttributes(int dataFormat, int dataType) {
            this.format = dataFormat;
            this.type = dataType;
        }
        public String toString() {
            return "PixelAttributes[fFmt 0x"+Integer.toHexString(format)+", type 0x"+Integer.toHexString(type)+"]";
        }
    }    
    /** Allows user to interface with another toolkit to define {@link PixelAttributes} and memory buffer to produce {@link TextureData}. */ 
    public static interface PixelBufferProvider {
        /** Called first to determine {@link PixelAttributes}. */
        PixelAttributes getAttributes(GL gl, int componentCount);
        
        /** 
         * Returns true, if implementation requires a new buffer based on the new size 
         * and previous aquired {@link #getAttributes(GL, int) attributes} due to pixel alignment, otherwise false.
         * @see #allocate(int, int, int)
         */
        boolean requiresNewBuffer(int width, int height);
        
        /** 
         * Called after {@link #getAttributes(GL, int)} to retrieve the NIO or array backed pixel {@link Buffer}.
         * <p>
         * Being called to gather the initial {@link Buffer}, if the existing {@link Buffer} size is not sufficient,
         * or if {@link #requiresNewBuffer(int, int)} returns false.
         * </p>
         * <p>
         * Number of components was passed via {@link #getAttributes(GL, int)}.
         * </p>
         * <p>
         * The returned buffer must have at least <code>minByteSize</code> {@link Buffer#remaining() remaining}.
         * </p>
         */
        Buffer allocate(int width, int height, int minByteSize);
        
        /** Dispose resources. */
        void dispose();
    }
    /** 
     * Default {@link PixelBufferProvider} utilizing best match for {@link PixelAttributes}
     * and {@link #allocate(int, int, int) allocating} a {@link ByteBuffer}.
     */
    public static class DefPixelBufferProvider implements PixelBufferProvider {
        @Override
        public PixelAttributes getAttributes(GL gl, int componentCount) {
            final GLContext ctx = gl.getContext();
            final int dFormat, dType;
            
            if(gl.isGL2GL3() && 3 == componentCount) {
                dFormat = GL.GL_RGB;
                dType   = GL.GL_UNSIGNED_BYTE;            
            } else {
                dFormat = ctx.getDefaultPixelDataFormat();
                dType   = ctx.getDefaultPixelDataType();
            }
            return new TextureData.PixelAttributes(dFormat, dType);
        }        
        @Override
        public boolean requiresNewBuffer(int width, int height) {
            return false;
        }
        /**
         * {@inheritDoc}
         * <p>
         * Returns an NIO {@link ByteBuffer} of <code>minByteSize</code>.
         * </p>
         */
        @Override
        public final Buffer allocate(int width, int height, int minByteSize) {
            return Buffers.newDirectByteBuffer(minByteSize);
        }
        
        @Override
        public void dispose() {
            // nop
        }
    }
    
    protected int width;
    protected int height;
    private int border;
    protected PixelAttributes pixelAttributes;
    protected int internalFormat; // perhaps inferred from pixelFormat?
    protected boolean mipmap; // indicates whether mipmaps should be generated
    // (ignored if mipmaps are supplied from the file)
    private boolean dataIsCompressed;
    protected boolean mustFlipVertically; // Must flip texture coordinates
    // vertically to get OpenGL output
    // to look correct
    protected Buffer buffer; // the actual data...
    private Buffer[] mipmapData; // ...or a series of mipmaps
    private Flusher flusher;
    protected int rowLength;
    protected int alignment; // 1, 2, or 4 bytes
    protected int estimatedMemorySize;

    // These booleans are a concession to the AWTTextureData subclass
    protected boolean haveEXTABGR;
    protected boolean haveGL12;
    protected GLProfile glProfile;
    protected ColorSpace pixelCS = ColorSpace.RGB;

    /** 
     * Constructs a new TextureData object with the specified parameters
     * and data contained in the given Buffer. The optional Flusher can
     * be used to clean up native resources associated with this
     * TextureData when processing is complete; for example, closing of
     * memory-mapped files that might otherwise require a garbage
     * collection to reclaim and close.
     *
     * @param glp the OpenGL Profile this texture data should be
     *                  created for.
     * @param internalFormat the OpenGL internal format for the
     *                       resulting texture; must be specified, may
     *                       not be 0
     * @param width          the width in pixels of the texture
     * @param height         the height in pixels of the texture
     * @param border         the number of pixels of border this texture
     *                       data has (0 or 1)
     * @param pixelFormat    the OpenGL pixel format for the
     *                       resulting texture; must be specified, may
     *                       not be 0
     * @param pixelType      the OpenGL type of the pixels of the texture
     * @param mipmap         indicates whether mipmaps should be
     *                       autogenerated (using GLU) for the resulting
     *                       texture. Currently if mipmap is true then
     *                       dataIsCompressed may not be true.
     * @param dataIsCompressed indicates whether the texture data is in
     *                       compressed form
     *                       (e.g. GL_COMPRESSED_RGB_S3TC_DXT1_EXT)
     * @param mustFlipVertically indicates whether the texture
     *                           coordinates must be flipped vertically
     *                           in order to properly display the
     *                           texture
     * @param buffer         the buffer containing the texture data
     * @param flusher        optional flusher to perform cleanup tasks
     *                       upon call to flush()
     *
     * @throws IllegalArgumentException if any parameters of the texture
     *   data were invalid, such as requesting mipmap generation for a
     *   compressed texture
     */
    public TextureData(GLProfile glp, 
                       int internalFormat,
                       int width,
                       int height,
                       int border,
                       int pixelFormat,
                       int pixelType,
                       boolean mipmap,
                       boolean dataIsCompressed,
                       boolean mustFlipVertically,
                       Buffer buffer,
                       Flusher flusher) throws IllegalArgumentException {
        this(glp, internalFormat, width, height, border, new PixelAttributes(pixelFormat, pixelType), 
             mipmap, dataIsCompressed, mustFlipVertically, buffer, flusher);
    }

    /** 
     * Constructs a new TextureData object with the specified parameters
     * and data contained in the given Buffer. The optional Flusher can
     * be used to clean up native resources associated with this
     * TextureData when processing is complete; for example, closing of
     * memory-mapped files that might otherwise require a garbage
     * collection to reclaim and close.
     *
     * @param glp the OpenGL Profile this texture data should be
     *                  created for.
     * @param internalFormat the OpenGL internal format for the
     *                       resulting texture; must be specified, may
     *                       not be 0
     * @param width          the width in pixels of the texture
     * @param height         the height in pixels of the texture
     * @param border         the number of pixels of border this texture
     *                       data has (0 or 1)
     * @param pixelAttributes the OpenGL pixel format and type for the
     *                       resulting texture; must be specified, may
     *                       not be 0
     * @param mipmap         indicates whether mipmaps should be
     *                       autogenerated (using GLU) for the resulting
     *                       texture. Currently if mipmap is true then
     *                       dataIsCompressed may not be true.
     * @param dataIsCompressed indicates whether the texture data is in
     *                       compressed form
     *                       (e.g. GL_COMPRESSED_RGB_S3TC_DXT1_EXT)
     * @param mustFlipVertically indicates whether the texture
     *                           coordinates must be flipped vertically
     *                           in order to properly display the
     *                           texture
     * @param buffer         the buffer containing the texture data
     * @param flusher        optional flusher to perform cleanup tasks
     *                       upon call to flush()
     *
     * @throws IllegalArgumentException if any parameters of the texture
     *   data were invalid, such as requesting mipmap generation for a
     *   compressed texture
     */
    public TextureData(GLProfile glp, 
                       int internalFormat,
                       int width,
                       int height,
                       int border,
                       PixelAttributes pixelAttributes,
                       boolean mipmap,
                       boolean dataIsCompressed,
                       boolean mustFlipVertically,
                       Buffer buffer,
                       Flusher flusher) throws IllegalArgumentException {
        if (mipmap && dataIsCompressed) {
            throw new IllegalArgumentException("Can not generate mipmaps for compressed textures");
        }

        this.glProfile = glp;
        this.width = width;
        this.height = height;
        this.border = border;
        this.pixelAttributes = pixelAttributes;
        this.internalFormat = internalFormat;
        this.mipmap = mipmap;
        this.dataIsCompressed = dataIsCompressed;
        this.mustFlipVertically = mustFlipVertically;
        this.buffer = buffer;
        this.flusher = flusher;
        alignment = 1;  // FIXME: is this correct enough in all situations?
        estimatedMemorySize = estimatedMemorySize(buffer);
    }
    
    /** 
     * Constructs a new TextureData object with the specified parameters
     * and data for multiple mipmap levels contained in the given array
     * of Buffers. The optional Flusher can be used to clean up native
     * resources associated with this TextureData when processing is
     * complete; for example, closing of memory-mapped files that might
     * otherwise require a garbage collection to reclaim and close.
     *
     * @param glp the OpenGL Profile this texture data should be
     *                  created for.
     * @param internalFormat the OpenGL internal format for the
     *                       resulting texture; must be specified, may
     *                       not be 0
     * @param width          the width in pixels of the topmost mipmap
     *                       level of the texture
     * @param height         the height in pixels of the topmost mipmap
     *                       level of the texture
     * @param border         the number of pixels of border this texture
     *                       data has (0 or 1)
     * @param pixelFormat    the OpenGL pixel format for the
     *                       resulting texture; must be specified, may
     *                       not be 0
     * @param pixelType      the OpenGL type of the pixels of the texture
     * @param dataIsCompressed indicates whether the texture data is in
     *                       compressed form
     *                       (e.g. GL_COMPRESSED_RGB_S3TC_DXT1_EXT)
     * @param mustFlipVertically indicates whether the texture
     *                           coordinates must be flipped vertically
     *                           in order to properly display the
     *                           texture
     * @param mipmapData     the buffers containing all mipmap levels
     *                       of the texture's data
     * @param flusher        optional flusher to perform cleanup tasks
     *                       upon call to flush()
     *
     * @throws IllegalArgumentException if any parameters of the texture
     *   data were invalid, such as requesting mipmap generation for a
     *   compressed texture
     */
    public TextureData(GLProfile glp,
                       int internalFormat,
                       int width,
                       int height,
                       int border,
                       int pixelFormat,
                       int pixelType,
                       boolean dataIsCompressed,
                       boolean mustFlipVertically,
                       Buffer[] mipmapData,
                       Flusher flusher) throws IllegalArgumentException {
        this(glp, internalFormat, width, height, border, new PixelAttributes(pixelFormat, pixelType), 
             dataIsCompressed, mustFlipVertically, mipmapData, flusher);
    }

    /** 
     * Constructs a new TextureData object with the specified parameters
     * and data for multiple mipmap levels contained in the given array
     * of Buffers. The optional Flusher can be used to clean up native
     * resources associated with this TextureData when processing is
     * complete; for example, closing of memory-mapped files that might
     * otherwise require a garbage collection to reclaim and close.
     *
     * @param glp the OpenGL Profile this texture data should be
     *                  created for.
     * @param internalFormat the OpenGL internal format for the
     *                       resulting texture; must be specified, may
     *                       not be 0
     * @param width          the width in pixels of the topmost mipmap
     *                       level of the texture
     * @param height         the height in pixels of the topmost mipmap
     *                       level of the texture
     * @param border         the number of pixels of border this texture
     *                       data has (0 or 1)
     * @param pixelAttributes the OpenGL pixel format and type for the
     *                       resulting texture; must be specified, may
     *                       not be 0
     * @param dataIsCompressed indicates whether the texture data is in
     *                       compressed form
     *                       (e.g. GL_COMPRESSED_RGB_S3TC_DXT1_EXT)
     * @param mustFlipVertically indicates whether the texture
     *                           coordinates must be flipped vertically
     *                           in order to properly display the
     *                           texture
     * @param mipmapData     the buffers containing all mipmap levels
     *                       of the texture's data
     * @param flusher        optional flusher to perform cleanup tasks
     *                       upon call to flush()
     *
     * @throws IllegalArgumentException if any parameters of the texture
     *   data were invalid, such as requesting mipmap generation for a
     *   compressed texture
     */
    public TextureData(GLProfile glp,
                       int internalFormat,
                       int width,
                       int height,
                       int border,
                       PixelAttributes pixelAttributes,
                       boolean dataIsCompressed,
                       boolean mustFlipVertically,
                       Buffer[] mipmapData,
                       Flusher flusher) throws IllegalArgumentException {
        this.glProfile = glp;
        this.width = width;
        this.height = height;
        this.border = border;
        this.pixelAttributes = pixelAttributes;
        this.internalFormat = internalFormat;
        this.dataIsCompressed = dataIsCompressed;
        this.mustFlipVertically = mustFlipVertically;
        this.mipmapData = (Buffer[]) mipmapData.clone();
        this.flusher = flusher;
        alignment = 1;  // FIXME: is this correct enough in all situations?
        for (int i = 0; i < mipmapData.length; i++) {
            estimatedMemorySize += estimatedMemorySize(mipmapData[i]);
        }
    }
    
    /** 
     * Returns the color space of the pixel data.
     * @see #setColorSpace(ColorSpace) 
     */
    public ColorSpace getColorSpace() { return pixelCS; }

    /** 
     * Set the color space of the pixel data, which defaults to {@link ColorSpace#RGB}.
     * @see #getColorSpace() 
     */
    public void setColorSpace(ColorSpace cs) { pixelCS = cs; }
    
    /** Used only by subclasses */
    protected TextureData(GLProfile glp) { this.glProfile = glp; this.pixelAttributes = PixelAttributes.UNDEF; }

    /** Returns the width in pixels of the texture data. */
    public int getWidth() { return width; }
    /** Returns the height in pixels of the texture data. */
    public int getHeight() { return height; }
    /** Returns the border in pixels of the texture data. */
    public int getBorder() { 
        return border; 
    }
    /** Returns the intended OpenGL {@link PixelAttributes} of the texture data, i.e. format and type. */
    public PixelAttributes getPixelAttributes() {
        return pixelAttributes;
    }
    /** Returns the intended OpenGL pixel format of the texture data. */
    public int getPixelFormat() {
        return pixelAttributes.format;
    }
    /** Returns the intended OpenGL pixel type of the texture data. */
    public int getPixelType() {
        return pixelAttributes.type;
    }
    /** Returns the intended OpenGL internal format of the texture data. */
    public int getInternalFormat() { 
        return internalFormat; 
    }
    /** Returns whether mipmaps should be generated for the texture data. */
    public boolean getMipmap() { 
        return mipmap; 
    }
    /** Indicates whether the texture data is in compressed form. */
    public boolean isDataCompressed() { 
        return dataIsCompressed; 
    }
    /** Indicates whether the texture coordinates must be flipped
        vertically for proper display. */
    public boolean getMustFlipVertically() { 
        return mustFlipVertically; 
    }
    /** Returns the texture data, or null if it is specified as a set of mipmaps. */
    public Buffer getBuffer() {
        return buffer;
    }
    /** Returns all mipmap levels for the texture data, or null if it is
        specified as a single image. */
    public Buffer[] getMipmapData() { 
        return mipmapData; 
    }
    /** Returns the required byte alignment for the texture data. */
    public int getAlignment() { 
        return alignment; 
    }
    /** Returns the row length needed for correct GL_UNPACK_ROW_LENGTH
        specification. This is currently only supported for
        non-mipmapped, non-compressed textures. */
    public int getRowLength() { 
        return rowLength; 
    }

    /** Sets the width in pixels of the texture data. */
    public void setWidth(int width) { this.width = width; }
    /** Sets the height in pixels of the texture data. */
    public void setHeight(int height) { this.height = height; }
    /** Sets the border in pixels of the texture data. */
    public void setBorder(int border) { this.border = border; }
    /** Sets the intended OpenGL pixel format of the texture data. */
    public void setPixelAttributes(PixelAttributes pixelAttributes) { this.pixelAttributes = pixelAttributes; }     
    /** 
     * Sets the intended OpenGL pixel format component of {@link PixelAttributes} of the texture data.
     * <p>
     * Use {@link #setPixelAttributes(PixelAttributes)}, if setting format and type. 
     * </p> 
     */
    public void setPixelFormat(int pixelFormat) {
        if( pixelAttributes.format != pixelFormat ) {
            pixelAttributes = new PixelAttributes(pixelFormat, pixelAttributes.type);
        }
    }
    /** 
     * Sets the intended OpenGL pixel type component of {@link PixelAttributes} of the texture data.
     * <p>
     * Use {@link #setPixelAttributes(PixelAttributes)}, if setting format and type. 
     * </p> 
     */
    public void setPixelType(int pixelType) { 
        if( pixelAttributes.type != pixelType) {
            pixelAttributes = new PixelAttributes(pixelAttributes.format, pixelType);
        }
    }
    /** Sets the intended OpenGL internal format of the texture data. */
    public void setInternalFormat(int internalFormat) { this.internalFormat = internalFormat; }
    /** Sets whether mipmaps should be generated for the texture data. */
    public void setMipmap(boolean mipmap) { this.mipmap = mipmap; }
    /** Sets whether the texture data is in compressed form. */
    public void setIsDataCompressed(boolean compressed) { this.dataIsCompressed = compressed; }
    /** Sets whether the texture coordinates must be flipped vertically
        for proper display. */
    public void setMustFlipVertically(boolean mustFlipVertically) { this.mustFlipVertically = mustFlipVertically; }
    /** Sets the texture data. */
    public void setBuffer(Buffer buffer) {
        this.buffer = buffer;
        estimatedMemorySize = estimatedMemorySize(buffer);
    }
    /** Sets the required byte alignment for the texture data. */
    public void setAlignment(int alignment) { this.alignment = alignment; }
    /** Sets the row length needed for correct GL_UNPACK_ROW_LENGTH
        specification. This is currently only supported for
        non-mipmapped, non-compressed textures. */
    public void setRowLength(int rowLength) { this.rowLength = rowLength; }
    /** Indicates to this TextureData whether the GL_EXT_abgr extension
        is available. Used for optimization along some code paths to
        avoid data copies. */
    public void setHaveEXTABGR(boolean haveEXTABGR) {
        this.haveEXTABGR = haveEXTABGR;
    }
    /** Indicates to this TextureData whether OpenGL version 1.2 is
        available. If not, falls back to relatively inefficient code
        paths for several input data types (several kinds of packed
        pixel formats, in particular). */
    public void setHaveGL12(boolean haveGL12) {
        this.haveGL12 = haveGL12;
    }

    /** Returns the GLProfile this texture data is intended and created for. */
    public GLProfile getGLProfile() { return glProfile; }

    /** Returns an estimate of the amount of memory in bytes this
        TextureData will consume once uploaded to the graphics card. It
        should only be treated as an estimate; most applications should
        not need to query this but instead let the OpenGL implementation
        page textures in and out as necessary. */
    public int getEstimatedMemorySize() {
        return estimatedMemorySize;
    }

    /** Flushes resources associated with this TextureData by calling
        Flusher.flush(). */
    public void flush() {
        if (flusher != null) {
            flusher.flush();
            flusher = null;
        }
    }

    /** Calls flush()
     * @see #flush()
     */
    public void destroy() {
        flush();
    }

    /** Defines a callback mechanism to allow the user to explicitly
        deallocate native resources (memory-mapped files, etc.)
        associated with a particular TextureData. */
    public static interface Flusher {
        /** Flushes any native resources associated with this
            TextureData. */
        public void flush();
    }

    public String toString() {
        return "TextureData["+width+"x"+height+", y-flip "+mustFlipVertically+", internFormat 0x"+Integer.toHexString(internalFormat)+", "+
                pixelAttributes+", border "+border+", estSize "+estimatedMemorySize+", alignment "+alignment+", rowlen "+rowLength;
    }

    //----------------------------------------------------------------------
    // Internals only below this point
    //

    protected static int estimatedMemorySize(Buffer buffer) {
        if (buffer == null) {
            return 0;
        }
        return buffer.capacity() * GLBuffers.sizeOfBufferElem(buffer);
    }
}
