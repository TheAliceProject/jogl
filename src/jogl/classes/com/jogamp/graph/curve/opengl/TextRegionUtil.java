/**
 * Copyright 2014-2023 JogAmp Community. All rights reserved.
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
package com.jogamp.graph.curve.opengl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

import com.jogamp.opengl.GL2ES2;
import com.jogamp.opengl.GLException;
import com.jogamp.opengl.math.geom.AABBox;
import com.jogamp.graph.curve.OutlineShape;
import com.jogamp.graph.curve.Region;
import com.jogamp.graph.font.Font;
import com.jogamp.graph.geom.Vertex;
import com.jogamp.graph.geom.Vertex.Factory;
import com.jogamp.graph.geom.plane.AffineTransform;

/**
 * Text Type Rendering Utility Class adding the {@link Font.Glyph}s {@link OutlineShape} to a {@link GLRegion}.
 * <p>
 * {@link OutlineShape}s are all produced in font em-size [0..1].
 * </p>
 */
public class TextRegionUtil {

    public final int renderModes;

    public TextRegionUtil(final int renderModes) {
        this.renderModes = renderModes;
    }

    public static int getCharCount(final String s, final char c) {
        final int sz = s.length();
        int count = 0;
        for(int i=0; i<sz; i++) {
            if( s.charAt(i) == c ) {
                count++;
            }
        }
        return count;
    }

    /**
     * Add the string in 3D space w.r.t. the font in font em-size [0..1] at the end of the {@link GLRegion}.
     * <p>
     * The shapes added to the GLRegion are in font em-size [0..1].
     * </p>
     * @param region the {@link GLRegion} sink
     * @param vertexFactory vertex impl factory {@link Factory}
     * @param font the target {@link Font}
     * @param str string text
     * @param rgbaColor if {@link Region#hasColorChannel()} RGBA color must be passed, otherwise value is ignored.
     * @param temp1 temporary AffineTransform storage, mandatory
     * @param temp2 temporary AffineTransform storage, mandatory
     * @return the bounding box of the given string by taking each glyph's font em-sized [0..1] OutlineShape into account.
     */
    public static AABBox addStringToRegion(final GLRegion region, final Factory<? extends Vertex> vertexFactory,
                                           final Font font, final CharSequence str, final float[] rgbaColor,
                                           final AffineTransform temp1, final AffineTransform temp2) {
        final OutlineShape.Visitor visitor = new OutlineShape.Visitor() {
            @Override
            public final void visit(final OutlineShape shape, final AffineTransform t) {
                region.addOutlineShape(shape, t, region.hasColorChannel() ? rgbaColor : null);
            } };
        return font.processString(visitor, null, str, temp1, temp2);
    }

    /**
     * Render the string in 3D space w.r.t. the font int font em-size [0..1] at the end of an internally cached {@link GLRegion}.
     * <p>
     * The shapes added to the GLRegion are in font em-size [0..1].
     * </p>
     * <p>
     * Cached {@link GLRegion}s will be destroyed w/ {@link #clear(GL2ES2)} or to free memory.
     * </p>
     * @param gl the current GL state
     * @param renderer TODO
     * @param font {@link Font} to be used
     * @param str text to be rendered
     * @param rgbaColor if {@link Region#hasColorChannel()} RGBA color must be passed, otherwise value is ignored.
     * @param sampleCount desired multisampling sample count for msaa-rendering.
     *        The actual used scample-count is written back when msaa-rendering is enabled, otherwise the store is untouched.
     * @return the bounding box of the given string from the produced and rendered GLRegion
     * @throws Exception if TextRenderer not initialized
     */
    public AABBox drawString3D(final GL2ES2 gl,
                               final RegionRenderer renderer, final Font font, final CharSequence str,
                               final float[] rgbaColor, final int[/*1*/] sampleCount) {
        if( !renderer.isInitialized() ) {
            throw new GLException("TextRendererImpl01: not initialized!");
        }
        final int special = 0;
        GLRegion region = getCachedRegion(font, str, special);
        if(null == region) {
            region = GLRegion.create(renderModes, null);
            addStringToRegion(region, renderer.getRenderState().getVertexFactory(), font, str, rgbaColor, tempT1, tempT2);
            addCachedRegion(gl, font, str, special, region);
        }
        final AABBox res = new AABBox();
        res.copy(region.getBounds());
        region.draw(gl, renderer, sampleCount);
        return res;
    }

    /**
     * Render the string in 3D space w.r.t. the font in font em-size [0..1] at the end of an internally temporary {@link GLRegion}.
     * <p>
     * The shapes added to the GLRegion are in font em-size [0..1].
     * </p>
     * <p>
     * In case of a multisampling region renderer, i.e. {@link Region#VBAA_RENDERING_BIT}, recreating the {@link GLRegion}
     * is a huge performance impact.
     * In such case better use {@link #drawString3D(GL2ES2, GLRegion, RegionRenderer, Font, CharSequence, float[], int[])}
     * instead.
     * </p>
     * @param gl the current GL state
     * @param renderModes TODO
     * @param font {@link Font} to be used
     * @param str text to be rendered
     * @param rgbaColor if {@link Region#hasColorChannel()} RGBA color must be passed, otherwise value is ignored.
     * @param sampleCount desired multisampling sample count for msaa-rendering.
     *        The actual used scample-count is written back when msaa-rendering is enabled, otherwise the store is untouched.
     * @throws Exception if TextRenderer not initialized
     * @return the bounding box of the given string from the produced and rendered GLRegion
     */
    public static AABBox drawString3D(final GL2ES2 gl, final int renderModes,
                                      final RegionRenderer renderer, final Font font, final CharSequence str,
                                      final float[] rgbaColor, final int[/*1*/] sampleCount) {
        if(!renderer.isInitialized()){
            throw new GLException("TextRendererImpl01: not initialized!");
        }
        final AffineTransform temp1 = new AffineTransform();
        final AffineTransform temp2 = new AffineTransform();
        final GLRegion region = GLRegion.create(renderModes, null);
        addStringToRegion(region, renderer.getRenderState().getVertexFactory(), font, str, rgbaColor, temp1, temp2);
        final AABBox res = new AABBox();
        res.copy(region.getBounds());
        region.draw(gl, renderer, sampleCount);
        region.destroy(gl);
        return res;
    }

    /**
     * Render the string in 3D space w.r.t. the font in font em-size [0..1] at the end of the given {@link GLRegion},
     * which will {@link GLRegion#clear(GL2ES2) cleared} beforehand.
     * <p>
     * The shapes added to the GLRegion are in font em-size [0..1].
     * </p>
     * @param gl the current GL state
     * @param font {@link Font} to be used
     * @param str text to be rendered
     * @param rgbaColor if {@link Region#hasColorChannel()} RGBA color must be passed, otherwise value is ignored.
     * @param sampleCount desired multisampling sample count for msaa-rendering.
     *        The actual used scample-count is written back when msaa-rendering is enabled, otherwise the store is untouched.
     * @return the bounding box of the given string from the produced and rendered GLRegion
     * @throws Exception if TextRenderer not initialized
     */
    public static AABBox drawString3D(final GL2ES2 gl, final GLRegion region, final RegionRenderer renderer,
                                      final Font font, final CharSequence str, final float[] rgbaColor,
                                      final int[/*1*/] sampleCount) {
        if(!renderer.isInitialized()){
            throw new GLException("TextRendererImpl01: not initialized!");
        }
        final AffineTransform temp1 = new AffineTransform();
        final AffineTransform temp2 = new AffineTransform();
        region.clear(gl);
        addStringToRegion(region, renderer.getRenderState().getVertexFactory(), font, str, rgbaColor, temp1, temp2);
        final AABBox res = new AABBox();
        res.copy(region.getBounds());
        region.draw(gl, renderer, sampleCount);
        return res;
    }

   /**
    * Clear all cached {@link GLRegions}.
    */
   public void clear(final GL2ES2 gl) {
       // fluchCache(gl) already called
       final Iterator<GLRegion> iterator = stringCacheMap.values().iterator();
       while(iterator.hasNext()){
           final GLRegion region = iterator.next();
           region.destroy(gl);
       }
       stringCacheMap.clear();
       stringCacheArray.clear();
   }

   /**
    * <p>Sets the cache limit for reusing GlyphString's and their Region.
    * Default is {@link #DEFAULT_CACHE_LIMIT}, -1 unlimited, 0 turns cache off, >0 limited </p>
    *
    * <p>The cache will be validate when the next string rendering happens.</p>
    *
    * @param newLimit new cache size
    *
    * @see #DEFAULT_CACHE_LIMIT
    */
   public final void setCacheLimit(final int newLimit ) { stringCacheLimit = newLimit; }

   /**
    * Sets the cache limit, see {@link #setCacheLimit(int)} and validates the cache.
    *
    * @see #setCacheLimit(int)
    *
    * @param gl current GL used to remove cached objects if required
    * @param newLimit new cache size
    */
   public final void setCacheLimit(final GL2ES2 gl, final int newLimit ) { stringCacheLimit = newLimit; validateCache(gl, 0); }

   /**
    * @return the current cache limit
    */
   public final int getCacheLimit() { return stringCacheLimit; }

   /**
    * @return the current utilized cache size, <= {@link #getCacheLimit()}
    */
   public final int getCacheSize() { return stringCacheArray.size(); }

   protected final void validateCache(final GL2ES2 gl, final int space) {
       if ( getCacheLimit() > 0 ) {
           while ( getCacheSize() + space > getCacheLimit() ) {
               removeCachedRegion(gl, 0);
           }
       }
   }

   protected final GLRegion getCachedRegion(final Font font, final CharSequence str, final int special) {
       return stringCacheMap.get(getKey(font, str, special));
   }

   protected final void addCachedRegion(final GL2ES2 gl, final Font font, final CharSequence str, final int special, final GLRegion glyphString) {
       if ( 0 != getCacheLimit() ) {
           final String key = getKey(font, str, special);
           final GLRegion oldRegion = stringCacheMap.put(key, glyphString);
           if ( null == oldRegion ) {
               // new entry ..
               validateCache(gl, 1);
               stringCacheArray.add(stringCacheArray.size(), key);
           } /// else overwrite is nop ..
       }
   }

   protected final void removeCachedRegion(final GL2ES2 gl, final Font font, final CharSequence str, final int special) {
       final String key = getKey(font, str, special);
       final GLRegion region = stringCacheMap.remove(key);
       if(null != region) {
           region.destroy(gl);
       }
       stringCacheArray.remove(key);
   }

   protected final void removeCachedRegion(final GL2ES2 gl, final int idx) {
       final String key = stringCacheArray.remove(idx);
       if( null != key ) {
           final GLRegion region = stringCacheMap.remove(key);
           if(null != region) {
               region.destroy(gl);
           }
       }
   }

   protected final String getKey(final Font font, final CharSequence str, final int special) {
       final StringBuilder sb = new StringBuilder();
       return sb.append( font.getName(Font.NAME_UNIQUNAME) )
              .append(".").append(str.hashCode()).append(".").append(special).toString();
   }

   /** Default cache limit, see {@link #setCacheLimit(int)} */
   public static final int DEFAULT_CACHE_LIMIT = 256;

   public final AffineTransform tempT1 = new AffineTransform();
   public final AffineTransform tempT2 = new AffineTransform();
   private final HashMap<String, GLRegion> stringCacheMap = new HashMap<String, GLRegion>(DEFAULT_CACHE_LIMIT);
   private final ArrayList<String> stringCacheArray = new ArrayList<String>(DEFAULT_CACHE_LIMIT);
   private int stringCacheLimit = DEFAULT_CACHE_LIMIT;
}