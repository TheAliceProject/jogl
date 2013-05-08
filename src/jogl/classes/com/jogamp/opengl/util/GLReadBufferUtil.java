/**
 * Copyright 2010 JogAmp Community. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 * 
 *    1. Redistributions of source code must retain the above copyright notice, this list of
 *       conditions and the following disclaimer.
 * 
 *    2. Redistributions in binary form must reproduce the above copyright notice, this list
 *       of conditions and the following disclaimer in the documentation and/or other materials
 *       provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY JogAmp Community ``AS IS'' AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL JogAmp Community OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 * 
 * The views and conclusions contained in the software and documentation are those of the
 * authors and should not be interpreted as representing official policies, either expressed
 * or implied, of JogAmp Community.
 */
 
package com.jogamp.opengl.util;

import java.io.File;
import java.io.IOException;
import java.nio.Buffer;
import java.nio.ByteBuffer;

import javax.media.opengl.GL;
import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.GLDrawable;
import javax.media.opengl.GLException;

import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.util.texture.Texture;
import com.jogamp.opengl.util.texture.TextureData;
import com.jogamp.opengl.util.texture.TextureData.DefPixelBufferProvider;
import com.jogamp.opengl.util.texture.TextureData.PixelAttributes;
import com.jogamp.opengl.util.texture.TextureData.PixelBufferProvider;
import com.jogamp.opengl.util.texture.TextureIO;

/**
 * Utility to read out the current FB to TextureData, optionally writing the data back to a texture object.
 * <p>May be used directly to write the TextureData to file (screenshot).</p>
 */
public class GLReadBufferUtil {
    protected final PixelBufferProvider pixelBufferProvider;
    protected final int componentCount, alignment; 
    protected final Texture readTexture;
    protected final GLPixelStorageModes psm;    
    
    protected int readPixelSizeLast = 0;
    protected Buffer readPixelBuffer = null;
    protected TextureData readTextureData = null;

    /**
     * @param alpha true for RGBA readPixels, otherwise RGB readPixels. Disclaimer: Alpha maybe forced on ES platforms! 
     * @param write2Texture true if readPixel's TextureData shall be written to a 2d Texture
     */
    public GLReadBufferUtil(boolean alpha, boolean write2Texture) {
        this(new DefPixelBufferProvider(), alpha, write2Texture);
    }
    
    public GLReadBufferUtil(PixelBufferProvider pixelBufferProvider, boolean alpha, boolean write2Texture) {
        this.pixelBufferProvider = pixelBufferProvider;
        this.componentCount = alpha ? 4 : 3 ;
        this.alignment = alpha ? 4 : 1 ; 
        this.readTexture = write2Texture ? new Texture(GL.GL_TEXTURE_2D) : null ;
        this.psm = new GLPixelStorageModes();
    }
    
    /** Returns the {@link PixelBufferProvider} used by this instance. */
    public PixelBufferProvider getPixelBufferProvider() { return pixelBufferProvider; }
    
    public boolean isValid() {
      return null!=readTextureData && null!=readPixelBuffer ;
    }
    
    public boolean hasAlpha() { return 4 == componentCount ? true : false ; }
    
    public GLPixelStorageModes getGLPixelStorageModes() { return psm; }
    
    /**
     * Returns the raw pixel Buffer, filled by {@link #readPixels(GLAutoDrawable, boolean)}.
     * <p>
     * By default the {@link Buffer} is a {@link ByteBuffer}, due to {@link DefPixelBufferProvider#allocate(int, int, int)}.
     * If the {@link PixelBufferProvider} has changed via {@link #setPixelBufferProvider(PixelBufferProvider)}.
     * the {@link Buffer} type maybe different.
     * </p>
     */
    public Buffer getPixelBuffer() { return readPixelBuffer; }
    
    /**
     * rewind the raw pixel ByteBuffer
     */
    public void rewindPixelBuffer() { if( null != readPixelBuffer ) { readPixelBuffer.rewind(); } }

    /**
     * @return the resulting TextureData, filled by {@link #readPixels(GLAutoDrawable, boolean)}
     */
    public TextureData getTextureData() { return readTextureData; }
    
    /**
     * @return the Texture object filled by {@link #readPixels(GLAutoDrawable, boolean)},
     *         if this instance writes to a 2d Texture, otherwise null.
     * @see #GLReadBufferUtil(boolean, boolean)
     */
    public Texture getTexture() { return readTexture; }

    /**
     * Write the TextureData filled by {@link #readPixels(GLAutoDrawable, boolean)} to file
     */
    public void write(File dest) {
        try {
            TextureIO.write(readTextureData, dest);
            rewindPixelBuffer();
        } catch (IOException ex) {
            throw new RuntimeException("can not write to file: " + dest.getAbsolutePath(), ex);
        }
    }

    /**
     * Read the drawable's pixels to TextureData and Texture, if requested at construction.
     * 
     * @param gl the current GL context object. It's read drawable is being used as the pixel source.
     * @param mustFlipVertically indicates whether to flip the data vertically or not.
     *                           The context's drawable {@link GLDrawable#isGLOriented()} state
     *                           is taken into account.
     *                           Vertical flipping is propagated to TextureData
     *                           and handled in a efficient manner there (TextureCoordinates and TextureIO writer).
     * 
     * @see #GLReadBufferUtil(boolean, boolean)
     */
    public boolean readPixels(GL gl, boolean mustFlipVertically) {
        return readPixels(gl, 0, 0, null, null, mustFlipVertically);
    }
    
    /**
     * Read the drawable's pixels to TextureData and Texture, if requested at construction.
     * 
     * @param gl the current GL context object. It's read drawable is being used as the pixel source.
     * @param inX readPixel x offset
     * @param inY readPixel y offset
     * @param ioWidth readPixel width
     * @param ioHeight readPixel height
     * @param mustFlipVertically indicates whether to flip the data vertically or not.
     *                           The context's drawable {@link GLDrawable#isGLOriented()} state
     *                           is taken into account.
     *                           Vertical flipping is propagated to TextureData
     *                           and handled in a efficient manner there (TextureCoordinates and TextureIO writer).
     * 
     * @see #GLReadBufferUtil(boolean, boolean)
     */
    public boolean readPixels(GL gl, int inX, int inY, int ioWidth[], int ioHeight[], boolean mustFlipVertically) {
        final int glerr0 = gl.glGetError();
        if(GL.GL_NO_ERROR != glerr0) {
            System.err.println("Info: GLReadBufferUtil.readPixels: pre-exisiting GL error 0x"+Integer.toHexString(glerr0));
        }
        final PixelAttributes pixelAttribs = pixelBufferProvider.getAttributes(gl, componentCount);
        final int internalFormat;
        if(gl.isGL2GL3() && 3 == componentCount) {
            internalFormat = GL.GL_RGB;
        } else {
            internalFormat = (4 == componentCount) ? GL.GL_RGBA : GL.GL_RGB;
        }
        final GLDrawable drawable = gl.getContext().getGLReadDrawable();
        final int width, height;
        if( null == ioWidth || drawable.getWidth() < ioWidth[0] ) {
            width = drawable.getWidth();
        } else {
            width = ioWidth[0];
        }
        if( null == ioHeight || drawable.getHeight() < ioHeight[0] ) {
            height = drawable.getHeight();
        } else {
            height= ioHeight[0];
        }
        
        final boolean flipVertically;
        if( drawable.isGLOriented() ) {
            flipVertically = mustFlipVertically;
        } else {
            flipVertically = !mustFlipVertically;
        }
        
        final int tmp[] = new int[1];
        final int readPixelSize = GLBuffers.sizeof(gl, tmp, pixelAttribs.format, pixelAttribs.type, width, height, 1, true);
        
        boolean newData = false;
        if( readPixelSize > readPixelSizeLast || pixelBufferProvider.requiresNewBuffer(width, height) ) {
            readPixelBuffer = pixelBufferProvider.allocate(width, height, readPixelSize);
            Buffers.rangeCheckBytes(readPixelBuffer, readPixelSize);
            readPixelSizeLast = readPixelSize ;
            try {
                readTextureData = new TextureData(
                           gl.getGLProfile(),
                           internalFormat,
                           width, height,
                           0, 
                           pixelAttribs,
                           false, false, 
                           flipVertically,
                           readPixelBuffer,
                           null /* Flusher */);
                newData = true;
            } catch (Exception e) {
                readTextureData = null;
                readPixelBuffer = null;
                readPixelSizeLast = 0;
                throw new RuntimeException("can not fetch offscreen texture", e);
            }
        } else {
            readTextureData.setInternalFormat(internalFormat);
            readTextureData.setWidth(width);
            readTextureData.setHeight(height);
            readTextureData.setPixelAttributes(pixelAttribs);
        }
        boolean res = null!=readPixelBuffer;
        if(res) {
            psm.setAlignment(gl, alignment, alignment);
            readPixelBuffer.clear();
            try {
                gl.glReadPixels(inX, inY, width, height, pixelAttribs.format, pixelAttribs.type, readPixelBuffer);
            } catch(GLException gle) { res = false; gle.printStackTrace(); }
            readPixelBuffer.position( readPixelSize / Buffers.sizeOfBufferElem(readPixelBuffer) );
            readPixelBuffer.flip();
            final int glerr1 = gl.glGetError();
            if(GL.GL_NO_ERROR != glerr1) {
                System.err.println("GLReadBufferUtil.readPixels: readPixels error 0x"+Integer.toHexString(glerr1)+
                                   " "+width+"x"+height+
                                   ", "+pixelAttribs+
                                   ", "+readPixelBuffer+", sz "+readPixelSize);
                res = false;                
            }
            if(res && null != readTexture) {
                if(newData) {
                    readTexture.updateImage(gl, readTextureData);
                } else {
                    readTexture.updateSubImage(gl, readTextureData, 0, 
                                               0, 0, // src offset
                                               0, 0, // dst offset
                                               width, height);
                }
                readPixelBuffer.rewind();
            }
            psm.restore(gl);
        }
        return res;
    }

    public void dispose(GL gl) {  
        if(null != readTexture) {
            readTexture.destroy(gl);
            readTextureData = null;
        }
        if(null != readPixelBuffer) {
            readPixelBuffer = null;
        }
        readPixelSizeLast = 0;
        pixelBufferProvider.dispose();
    }

}
