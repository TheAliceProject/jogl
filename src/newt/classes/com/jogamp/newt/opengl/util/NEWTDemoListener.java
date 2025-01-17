/**
 * Copyright 2015 JogAmp Community. All rights reserved.
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
package com.jogamp.newt.opengl.util;

import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;

import com.jogamp.common.util.IOUtil;
import com.jogamp.graph.font.FontScale;
import com.jogamp.nativewindow.CapabilitiesImmutable;
import com.jogamp.nativewindow.ScalableSurface;
import com.jogamp.newt.Window;
import com.jogamp.newt.Display;
import com.jogamp.newt.Display.PointerIcon;
import com.jogamp.newt.event.KeyEvent;
import com.jogamp.newt.event.KeyListener;
import com.jogamp.newt.event.MouseEvent;
import com.jogamp.newt.event.MouseListener;
import com.jogamp.newt.event.WindowAdapter;
import com.jogamp.newt.event.WindowEvent;
import com.jogamp.newt.opengl.GLWindow;
import com.jogamp.opengl.FPSCounter;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GLAnimatorControl;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLDrawable;
import com.jogamp.opengl.GLRunnable;
import com.jogamp.opengl.util.Gamma;
import com.jogamp.opengl.util.PNGPixelRect;

import jogamp.newt.driver.PNGIcon;

/**
 * NEWT {@link GLWindow} Demo functionality
 * <ul>
 *   <li>SPACE: Toggle animator {@link GLAnimatorControl#pause() pause}/{@link GLAnimatorControl#resume() resume}</li>
 *   <li>A: Toggle window {@link Window#setAlwaysOnTop(boolean) always on top}</li>
 *   <li>B: Toggle window {@link Window#setAlwaysOnBottom(boolean) always on bottom}</li>
 *   <li>C: Toggle different {@link Window#setPointerIcon(PointerIcon) pointer icons}</li>
 *   <li>D: Toggle window {@link Window#setUndecorated(boolean) decoration on/off}</li>
 *   <li>F: Toggle window {@link Window#setFullscreen(boolean) fullscreen on/off}</li>
 *   <li>Three-Finger Double-Tap: Toggle window {@link Window#setFullscreen(boolean) fullscreen on/off}</li>
 *   <li>G: Increase {@link Gamma#setDisplayGamma(GLDrawable, float, float, float) gamma} by 0.1, +SHIFT decrease gamma by 0.1</li>
 *   <li>I: Toggle {@link Window#setPointerVisible(boolean) pointer visbility}</li>
 *   <li>J: Toggle {@link Window#confinePointer(boolean) pointer jail (confine to window)}</li>
 *   <li>M: Toggle {@link Window#setMaximized(boolean, boolean) window maximized}: Y, +CTRL off, +SHIFT toggle X+Y, +ALT X</li>
 *   <li>P: Set window {@link Window#setPosition(int, int) position to 100/100}</li>
 *   <li>Q: Quit</li>
 *   <li>R: Toggle window {@link Window#setResizable(boolean) resizable}</li>
 *   <li>S: Toggle window {@link Window#setSticky(boolean) sticky}</li>
 *   <li>V: Toggle window {@link Window#setVisible(boolean) visibility} for 5s</li>
 *   <li>V: +CTRL: Rotate {@link GL#setSwapInterval(int) swap interval} -1, 0, 1</li>
 *   <li>W: {@link Window#warpPointer(int, int) Warp pointer} to center of window</li>
 *   <li>X: Toggle {@link ScalableSurface#setSurfaceScale(float[]) [{@link ScalableSurface#IDENTITY_PIXELSCALE}, {@link ScalableSurface#AUTOMAX_PIXELSCALE}]</li>
 * </ul>
 */
public class NEWTDemoListener extends WindowAdapter implements KeyListener, MouseListener {
    protected final GLWindow glWindow;
    final PointerIcon[] pointerIcons;
    int pointerIconIdx = 0;
    float gamma = 1f;
    float brightness = 0f;
    float contrast = 1f;
    boolean confinedFixedCenter = false;

    /**
     * Creates a new instance with given pointer icons, which are not used if {@code null}.
     * @param glWin the GLWindow instance to use
     * @param pointerIcons if {@code null} don't use multiple pointer icons
     */
    public NEWTDemoListener(final GLWindow glWin, final PointerIcon[] pointerIcons) {
        this.glWindow = glWin;
        this.pointerIcons = pointerIcons;
    }
    /**
     * Creates a new instance with {@link #createPointerIcons(Display)} default pointer icons.
     * @param glWin the GLWindow instance to use
     */
    public NEWTDemoListener(final GLWindow glWin) {
        this(glWin, createPointerIcons(glWin.getScreen().getDisplay()));
    }

    protected void printlnState(final String prelude) {
        System.err.println(prelude+": "+glWindow.getX()+"/"+glWindow.getY()+" "+glWindow.getSurfaceWidth()+"x"+glWindow.getSurfaceHeight()+", f "+glWindow.isFullscreen()+", a "+glWindow.isAlwaysOnTop()+", "+glWindow.getInsets()+", state "+glWindow.getStateMaskString());
    }
    protected void printlnState(final String prelude, final String post) {
        System.err.println(prelude+": "+glWindow.getX()+"/"+glWindow.getY()+" "+glWindow.getSurfaceWidth()+"x"+glWindow.getSurfaceHeight()+", f "+glWindow.isFullscreen()+", a "+glWindow.isAlwaysOnTop()+", "+glWindow.getInsets()+", state "+glWindow.getStateMaskString()+", "+post);
    }

    @Override
    public void keyPressed(final KeyEvent e) {
        if( e.isAutoRepeat() || e.isConsumed() ) {
            return;
        }
        if( !glWindow.isVisible() ) {
            printlnState("[key "+e+" but invisible]");
            return;
        }
        final int keySymbol = e.getKeySymbol();
        switch(keySymbol) {
            case KeyEvent.VK_SPACE:
                e.setConsumed(true);
                glWindow.invokeOnCurrentThread(new Runnable() {
                    @Override
                    public void run() {
                        final GLAnimatorControl anim = glWindow.getAnimator();
                        if( null != anim ) {
                            if( anim.isPaused()) {
                                anim.resume();
                            } else {
                                anim.pause();
                            }
                        }
                    } } );
                break;
            case KeyEvent.VK_A:
                e.setConsumed(true);
                glWindow.invokeOnNewThread(null, false, new Runnable() {
                    @Override
                    public void run() {
                        printlnState("[set alwaysontop pre]");
                        glWindow.setAlwaysOnTop(!glWindow.isAlwaysOnTop());
                        printlnState("[set alwaysontop post]");
                    } } );
                break;
            case KeyEvent.VK_B:
                e.setConsumed(true);
                glWindow.invokeOnNewThread(null, false, new Runnable() {
                    @Override
                    public void run() {
                        printlnState("[set alwaysonbottom pre]");
                        glWindow.setAlwaysOnBottom(!glWindow.isAlwaysOnBottom());
                        printlnState("[set alwaysonbottom post]");
                    } } );
                break;
            case KeyEvent.VK_C:
                e.setConsumed(true);
                glWindow.invokeOnNewThread(null, false, new Runnable() {
                    @Override
                    public void run() {
                        if( null != pointerIcons ) {
                            printlnState("[set pointer-icon pre]");
                            final PointerIcon currentPI = glWindow.getPointerIcon();
                            final PointerIcon newPI;
                            if( pointerIconIdx >= pointerIcons.length ) {
                                newPI=null;
                                pointerIconIdx=0;
                            } else {
                                newPI=pointerIcons[pointerIconIdx++];
                            }
                            glWindow.setPointerIcon( newPI );
                            printlnState("[set pointer-icon post]", currentPI+" -> "+glWindow.getPointerIcon());
                        }
                    } } );
                break;
            case KeyEvent.VK_D:
                e.setConsumed(true);
                glWindow.invokeOnNewThread(null, false, new Runnable() {
                    @Override
                    public void run() {
                        printlnState("[set undecorated pre]");
                        glWindow.setUndecorated(!glWindow.isUndecorated());
                        printlnState("[set undecorated post]");
                    } } );
                break;
            case KeyEvent.VK_F:
                e.setConsumed(true);
                quitAdapterOff();
                glWindow.invokeOnNewThread(null, false, new Runnable() {
                    @Override
                    public void run() {
                        printlnState("[set fullscreen pre]");
                        if( glWindow.isFullscreen() ) {
                            glWindow.setFullscreen( false );
                        } else {
                            if( e.isAltDown() ) {
                                glWindow.setFullscreen( null );
                            } else {
                                glWindow.setFullscreen( true );
                            }
                        }
                        printlnState("[set fullscreen post]");
                        quitAdapterOn();
                    } } );
                break;
            case KeyEvent.VK_G:
                e.setConsumed(true);
                glWindow.invokeOnNewThread(null, false, new Runnable() {
                    @Override
                    public void run() {
                        final float newGamma = gamma + ( e.isShiftDown() ? -0.1f : 0.1f );
                        System.err.println("[set gamma]: "+gamma+" -> "+newGamma);
                        if( Gamma.setDisplayGamma(glWindow, newGamma, brightness, contrast) ) {
                            gamma = newGamma;
                        }
                    } } );
                break;
            case KeyEvent.VK_I:
                e.setConsumed(true);
                glWindow.invokeOnNewThread(null, false, new Runnable() {
                    @Override
                    public void run() {
                        printlnState("[set pointer-visible pre]");
                        glWindow.setPointerVisible(!glWindow.isPointerVisible());
                        printlnState("[set pointer-visible post]");
                    } } );
                break;
            case KeyEvent.VK_J:
                e.setConsumed(true);
                glWindow.invokeOnNewThread(null, false, new Runnable() {
                    @Override
                    public void run() {
                        printlnState("[set pointer-confined pre]", "warp-center: "+e.isShiftDown());
                        final boolean confine = !glWindow.isPointerConfined();
                        glWindow.confinePointer(confine);
                        printlnState("[set pointer-confined post]", "warp-center: "+e.isShiftDown());
                        if( e.isShiftDown() ) {
                            setConfinedFixedCenter(confine);
                        } else if( !confine ) {
                            setConfinedFixedCenter(false);
                        }
                    } } );
                break;
            case KeyEvent.VK_M:
                e.setConsumed(true);
                glWindow.invokeOnNewThread(null, false, new Runnable() {
                    @Override
                    public void run() {
                        // none:  max-v
                        // alt:   max-h
                        // shift: max-hv
                        // ctrl:  max-off
                        final boolean horz, vert;
                        if( e.isControlDown() ) {
                            horz = false;
                            vert = false;
                        } else if( e.isShiftDown() ) {
                            final boolean bothMax = glWindow.isMaximizedHorz() && glWindow.isMaximizedVert();
                            horz = !bothMax;
                            vert = !bothMax;
                        } else if( !e.isAltDown() ) {
                            horz = glWindow.isMaximizedHorz();
                            vert = !glWindow.isMaximizedVert();
                        } else if( e.isAltDown() ) {
                            horz = !glWindow.isMaximizedHorz();
                            vert = glWindow.isMaximizedVert();
                        } else {
                            vert = glWindow.isMaximizedVert();
                            horz = glWindow.isMaximizedHorz();
                        }
                        printlnState("[set maximize pre]", "max[vert "+vert+", horz "+horz+"]");
                        glWindow.setMaximized(horz, vert);
                        printlnState("[set maximize post]", "max[vert "+vert+", horz "+horz+"]");
                    } } );
                break;
            case KeyEvent.VK_P:
                e.setConsumed(true);
                glWindow.invokeOnNewThread(null, false, new Runnable() {
                    @Override
                    public void run() {
                        printlnState("[set position pre]");
                        glWindow.setPosition(100, 100);
                        printlnState("[set position post]");
                    } } );
                break;
            case KeyEvent.VK_Q:
                if( quitAdapterEnabled && 0 == e.getModifiers() ) {
                    System.err.println("QUIT Key "+Thread.currentThread());
                    quitAdapterShouldQuit = true;
                }
                break;
            case KeyEvent.VK_R:
                e.setConsumed(true);
                glWindow.invokeOnNewThread(null, false, new Runnable() {
                    @Override
                    public void run() {
                        printlnState("[set resizable pre]");
                        glWindow.setResizable(!glWindow.isResizable());
                        printlnState("[set resizable post]");
                    } } );
                break;
            case KeyEvent.VK_S:
                e.setConsumed(true);
                glWindow.invokeOnNewThread(null, false, new Runnable() {
                    @Override
                    public void run() {
                        printlnState("[set sticky pre]");
                        glWindow.setSticky(!glWindow.isSticky());
                        printlnState("[set sticky post]");
                    } } );
                break;
            case KeyEvent.VK_V:
                e.setConsumed(true);
                if( e.isControlDown() ) {
                    glWindow.invoke(false, new GLRunnable() {
                        @Override
                        public boolean run(final GLAutoDrawable drawable) {
                            final GL gl = drawable.getGL();
                            final int _i = gl.getSwapInterval();
                            final int i;
                            switch(_i) {
                                case  0: i = -1; break;
                                case -1: i =  1; break;
                                case  1: i =  0; break;
                                default: i =  1; break;
                            }
                            gl.setSwapInterval(i);

                            final GLAnimatorControl a = drawable.getAnimator();
                            if( null != a ) {
                                a.resetFPSCounter();
                            }
                            if(drawable instanceof FPSCounter) {
                                ((FPSCounter)drawable).resetFPSCounter();
                            }
                            System.err.println("Swap Interval: "+_i+" -> "+i+" -> "+gl.getSwapInterval());
                            return true;
                        }
                    });
                } else {
                    glWindow.invokeOnNewThread(null, false, new Runnable() {
                        @Override
                        public void run() {
                            final boolean wasVisible = glWindow.isVisible();
                            {
                                printlnState("[set visible pre]");
                                glWindow.setVisible(!wasVisible);
                                printlnState("[set visible post]");
                            }
                            if( wasVisible && !e.isShiftDown() ) {
                                try {
                                    java.lang.Thread.sleep(5000);
                                } catch (final InterruptedException e) {
                                    e.printStackTrace();
                                }
                                printlnState("[reset visible pre]");
                                glWindow.setVisible(true);
                                printlnState("[reset visible post]");
                            }
                    } } );
                }
                break;
            case KeyEvent.VK_W:
                e.setConsumed(true);
                glWindow.invokeOnNewThread(null, false, new Runnable() {
                    @Override
                    public void run() {
                        printlnState("[set pointer-pos pre]");
                        glWindow.warpPointer(glWindow.getSurfaceWidth()/2, glWindow.getSurfaceHeight()/2);
                        printlnState("[set pointer-pos post]");
                    } } );
                break;
            case KeyEvent.VK_X:
                e.setConsumed(true);
                final float[] hadSurfacePixelScale = glWindow.getCurrentSurfaceScale(new float[2]);
                final float[] reqSurfacePixelScale;
                if( hadSurfacePixelScale[0] == ScalableSurface.IDENTITY_PIXELSCALE ) {
                    reqSurfacePixelScale = new float[] { ScalableSurface.AUTOMAX_PIXELSCALE, ScalableSurface.AUTOMAX_PIXELSCALE };
                } else {
                    reqSurfacePixelScale = new float[] { ScalableSurface.IDENTITY_PIXELSCALE, ScalableSurface.IDENTITY_PIXELSCALE };
                }
                System.err.println("[set PixelScale pre]: had "+hadSurfacePixelScale[0]+"x"+hadSurfacePixelScale[1]+" -> req "+reqSurfacePixelScale[0]+"x"+reqSurfacePixelScale[1]);
                glWindow.setSurfaceScale(reqSurfacePixelScale);
                final float[] valReqSurfacePixelScale = glWindow.getRequestedSurfaceScale(new float[2]);
                final float[] hasSurfacePixelScale1 = glWindow.getCurrentSurfaceScale(new float[2]);
                System.err.println("[set PixelScale post]: "+hadSurfacePixelScale[0]+"x"+hadSurfacePixelScale[1]+" (had) -> "+
                        reqSurfacePixelScale[0]+"x"+reqSurfacePixelScale[1]+" (req) -> "+
                        valReqSurfacePixelScale[0]+"x"+valReqSurfacePixelScale[1]+" (val) -> "+
                        hasSurfacePixelScale1[0]+"x"+hasSurfacePixelScale1[1]+" (has)");
                setTitle();
        }
    }
    @Override
    public void keyReleased(final KeyEvent e) { }

    public void setConfinedFixedCenter(final boolean v) {
        confinedFixedCenter = v;
    }
    @Override
    public void mouseMoved(final MouseEvent e) {
        if( e.isConfined() ) {
            mouseCenterWarp(e);
        }
    }
    @Override
    public void mouseDragged(final MouseEvent e) {
        if( e.isConfined() ) {
            mouseCenterWarp(e);
        }
    }
    @Override
    public void mouseClicked(final MouseEvent e) {
        if(e.getClickCount() == 2 && e.getPointerCount() == 3) {
            glWindow.setFullscreen(!glWindow.isFullscreen());
            System.err.println("setFullscreen: "+glWindow.isFullscreen());
        }
    }
    private void mouseCenterWarp(final MouseEvent e) {
        if(e.isConfined() && confinedFixedCenter ) {
            final int x=glWindow.getSurfaceWidth()/2;
            final int y=glWindow.getSurfaceHeight()/2;
            glWindow.warpPointer(x, y);
        }
    }
    @Override
    public void mouseEntered(final MouseEvent e) {}
    @Override
    public void mouseExited(final MouseEvent e) {}
    @Override
    public void mousePressed(final MouseEvent e) {}
    @Override
    public void mouseReleased(final MouseEvent e) {}
    @Override
    public void mouseWheelMoved(final MouseEvent e) {}

    /////////////////////////////////////////////////////////////

    private boolean quitAdapterShouldQuit = false;
    private boolean quitAdapterEnabled = false;
    private boolean quitAdapterEnabled2 = true;

    protected void quitAdapterOff() {
        quitAdapterEnabled2 = false;
    }
    protected void quitAdapterOn() {
        clearQuitAdapter();
        quitAdapterEnabled2 = true;
    }
    public void quitAdapterEnable(final boolean v) { quitAdapterEnabled = v; }
    public void clearQuitAdapter() { quitAdapterShouldQuit = false; }
    public boolean shouldQuit() { return quitAdapterShouldQuit; }
    public void doQuit() { quitAdapterShouldQuit=true; }

    @Override
    public void windowDestroyNotify(final WindowEvent e) {
        if( quitAdapterEnabled && quitAdapterEnabled2 ) {
            System.err.println("QUIT Window "+Thread.currentThread());
            quitAdapterShouldQuit = true;
        }
    }

    /////////////////////////////////////////////////////////////

    public void setTitle() {
        setTitle(glWindow);
    }
    public static void setTitle(final GLWindow win) {
        final CapabilitiesImmutable chosenCaps = win.getChosenCapabilities();
        final CapabilitiesImmutable reqCaps = win.getRequestedCapabilities();
        final CapabilitiesImmutable caps = null != chosenCaps ? chosenCaps : reqCaps;
        final String capsA = caps.isBackgroundOpaque() ? "opaque" : "transl";
        final float[] sDPI = FontScale.perMMToPerInch( win.getPixelsPerMM(new float[2]) );
        final float[] minSurfacePixelScale = win.getMinimumSurfaceScale(new float[2]);
        final float[] maxSurfacePixelScale = win.getMaximumSurfaceScale(new float[2]);
        final float[] reqSurfacePixelScale = win.getRequestedSurfaceScale(new float[2]);
        final float[] hasSurfacePixelScale = win.getCurrentSurfaceScale(new float[2]);
        win.setTitle("GLWindow["+capsA+"], win: "+win.getBounds()+", pix: "+win.getSurfaceWidth()+"x"+win.getSurfaceHeight()+", sDPI "+sDPI[0]+" x "+sDPI[1]+
                ", scale[min "+minSurfacePixelScale[0]+"x"+minSurfacePixelScale[1]+", max "+
                maxSurfacePixelScale[0]+"x"+maxSurfacePixelScale[1]+", req "+
                reqSurfacePixelScale[0]+"x"+reqSurfacePixelScale[1]+" -> has "+
                hasSurfacePixelScale[0]+"x"+hasSurfacePixelScale[1]+"]");
    }

    public static PointerIcon[] createPointerIcons(final Display disp) {
        final List<PointerIcon> pointerIcons = new ArrayList<PointerIcon>();
        {
            disp.createNative();
            {
                PointerIcon _pointerIcon = null;
                final IOUtil.ClassResources res = new IOUtil.ClassResources(new String[] { "jogamp/newt/assets/cross-grey-alpha-16x16.png" }, disp.getClass().getClassLoader(), null);
                try {
                    _pointerIcon = disp.createPointerIcon(res, 8, 8);
                    pointerIcons.add(_pointerIcon);
                    System.err.printf("Create PointerIcon #%02d: %s%n", pointerIcons.size(), _pointerIcon.toString());
                } catch (final Exception e) {
                    System.err.println(e.getMessage());
                }
            }
            {
                PointerIcon _pointerIcon = null;
                final IOUtil.ClassResources res = new IOUtil.ClassResources(new String[] { "jogamp/newt/assets/pointer-grey-alpha-16x24.png" }, disp.getClass().getClassLoader(), null);
                try {
                    _pointerIcon = disp.createPointerIcon(res, 0, 0);
                    pointerIcons.add(_pointerIcon);
                    System.err.printf("Create PointerIcon #%02d: %s%n", pointerIcons.size(), _pointerIcon.toString());
                } catch (final Exception e) {
                    System.err.println(e.getMessage());
                }
            }
            {
                PointerIcon _pointerIcon = null;
                final IOUtil.ClassResources res = new IOUtil.ClassResources(new String[] { "arrow-red-alpha-64x64.png" }, disp.getClass().getClassLoader(), null);
                try {
                    _pointerIcon = disp.createPointerIcon(res, 0, 0);
                    pointerIcons.add(_pointerIcon);
                    System.err.printf("Create PointerIcon #%02d: %s%n", pointerIcons.size(), _pointerIcon.toString());
                } catch (final Exception e) {
                    System.err.println(e.getMessage());
                }
            }
            {
                PointerIcon _pointerIcon = null;
                final IOUtil.ClassResources res = new IOUtil.ClassResources(new String[] { "arrow-blue-alpha-64x64.png" }, disp.getClass().getClassLoader(), null);
                try {
                    _pointerIcon = disp.createPointerIcon(res, 0, 0);
                    pointerIcons.add(_pointerIcon);
                    System.err.printf("Create PointerIcon #%02d: %s%n", pointerIcons.size(), _pointerIcon.toString());
                } catch (final Exception e) {
                    System.err.println(e.getMessage());
                }
            }
            if( PNGIcon.isAvailable() ) {
                PointerIcon _pointerIcon = null;
                final IOUtil.ClassResources res = new IOUtil.ClassResources(new String[] { "jogamp-pointer-64x64.png" }, disp.getClass().getClassLoader(), null);
                try {
                    final URLConnection urlConn = res.resolve(0);
                    if( null != urlConn ) {
                        final PNGPixelRect image = PNGPixelRect.read(urlConn.getInputStream(), null, false /* directBuffer */, 0 /* destMinStrideInBytes */, false /* destIsGLOriented */);
                        System.err.printf("Create PointerIcon #%02d: %s%n", pointerIcons.size()+1, image.toString());
                        _pointerIcon = disp.createPointerIcon(image, 32, 0);
                        pointerIcons.add(_pointerIcon);
                        System.err.printf("Create PointerIcon #%02d: %s%n", pointerIcons.size(), _pointerIcon.toString());
                    }
                } catch (final Exception e) {
                    System.err.println(e.getMessage());
                }
            }
        }
        return pointerIcons.toArray(new PointerIcon[pointerIcons.size()]);
    }
}