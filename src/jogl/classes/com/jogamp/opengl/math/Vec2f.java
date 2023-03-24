/**
 * Copyright 2022-2023 JogAmp Community. All rights reserved.
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

package com.jogamp.opengl.math;

/**
 * 2D Vector based upon two float components.
 *
 * Implementation borrowed from [gfxbox2](https://jausoft.com/cgit/cs_class/gfxbox2.git/tree/include/pixel/pixel2f.hpp#n29)
 * and its layout from OpenAL's Vec3f.
 */
public final class Vec2f {
    private float x;
    private float y;

    public static Vec2f from_length_angle(final float magnitude, final float radians) {
        return new Vec2f((float)(magnitude * Math.cos(radians)), (float)(magnitude * Math.sin(radians)));
    }

    public Vec2f() {}

    public Vec2f(final Vec2f o) {
        this.x = o.x;
        this.y = o.y;
    }

    public Vec2f copy() {
        return new Vec2f(this);
    }

    public Vec2f(final float x, final float y) {
        this.x = x;
        this.y = y;
    }

    public void set(final Vec2f o) {
        this.x = o.x;
        this.y = o.y;
    }

    public void set(final float x, final float y) {
        this.x = x;
        this.y = y;
    }

    /** Sets the ith component, 0 <= i < 2 */
    public void set(final int i, final float val) {
        switch (i) {
            case 0: x = val; break;
            case 1: y = val; break;
            default: throw new IndexOutOfBoundsException();
        }
    }

    /** Gets the ith component, 0 <= i < 2 */
    public float get(final int i) {
        switch (i) {
            case 0: return x;
            case 1: return y;
            default: throw new IndexOutOfBoundsException();
        }
    }

    public float x() { return x; }
    public float y() { return y; }

    public void setX(final float x) { this.x = x; }
    public void setY(final float y) { this.y = y; }

    /** Returns this * val; creates new vector */
    public Vec2f mul(final float val) {
        return new Vec2f(this).scale(val);
    }

    /** this = this * val */
    public Vec2f scale(final float val) {
        x *= val;
        y *= val;
        return this;
    }

    /** Returns this + arg; creates new vector */
    public Vec2f plus(final Vec2f arg) {
        return new Vec2f(this).add(arg);
    }

    /** this = this + b */
    public Vec2f add(final Vec2f b) {
        x += b.x;
        y += b.y;
        return this;
    }

    /** Returns this + s * arg; creates new vector */
    public Vec2f plusScaled(final float s, final Vec2f arg) {
        return new Vec2f(this).addScaled(s, arg);
    }

    /** this = this + s * b */
    public Vec2f addScaled(final float s, final Vec2f b) {
        x += s * b.x;
        y += s * b.y;
        return this;
    }

    /** Returns this - arg; creates new vector */
    public Vec2f minus(final Vec2f arg) {
        return new Vec2f(this).sub(arg);
    }

    /** this = this - b */
    public Vec2f sub(final Vec2f b) {
        x -= b.x;
        y -= b.y;
        return this;
    }

    public boolean isZero() {
        return FloatUtil.isZero(x) && FloatUtil.isZero(y);
    }

    public void rotate(final float radians, final Vec2f ctr) {
        final float cos = (float)Math.cos(radians);
        final float sin = (float)Math.sin(radians);
        rotate(sin, cos, ctr);
    }

    public void rotate(final float sin, final float cos, final Vec2f ctr) {
        final float x0 = x - ctr.x;
        final float y0 = y - ctr.y;
        final float tmp = x0 * cos - y0 * sin + ctr.x;
        y = x0 * sin + y0 * cos + ctr.y;
        x = tmp;
    }

    /**
     * Return the length of this vector, a.k.a the <i>norm</i> or <i>magnitude</i>
     */
    public float length() {
        return (float) Math.sqrt(lengthSq());
    }

    /**
     * Return the squared length of this vector, a.k.a the squared <i>norm</i> or squared <i>magnitude</i>
     */
    public float lengthSq() {
        return x*x + y*y;
    }

    /**
     * Return the direction angle of this vector in radians
     */
    public float angle() {
        // Utilize atan2 taking y=sin(a) and x=cos(a), resulting in proper direction angle for all quadrants.
        return (float) Math.atan2(y, x);
    }

    /**
     * Normalize this vector in place
     */
    public Vec2f normalize() {
        final float lengthSq = lengthSq();
        if ( FloatUtil.isZero( lengthSq ) ) {
            x = 0.0f;
            y = 0.0f;
        } else {
            final float invSqr = 1.0f / (float)Math.sqrt(lengthSq);
            x *= invSqr;
            y *= invSqr;
        }
        return this;
    }

    /**
     * Return the squared distance between this vector and the given one.
     * <p>
     * When comparing the relative distance between two points it is usually sufficient to compare the squared
     * distances, thus avoiding an expensive square root operation.
     * </p>
     */
    public float distSq(final Vec2f o) {
        final float dx = x - o.x;
        final float dy = y - o.y;
        return dx*dx + dy*dy;
    }

    /**
     * Return the distance between this vector and the given one.
     */
    public float dist(final Vec2f o) {
        return (float)Math.sqrt(distSq(o));
    }


    /**
     * Return the dot product of this vector and the given one
     * @return the dot product as float
     */
    public float dot(final Vec2f arg) {
        return x * arg.x + y * arg.y;
    }

    /**
     * Returns cross product of this vectors and the given one, i.e. *this x o.
     *
     * The 2D cross product is identical with the 2D perp dot product.
     *
     * @return the resulting scalar
     */
    public float cross(final Vec2f o) {
        return x * o.y - y * o.x;
    }

    /**
     * Return the cosines of the angle between two vectors
     */
    public float cosAngle(final Vec2f o) {
        return dot(o) / ( length() * o.length() ) ;
    }

    /**
     * Return the angle between two vectors in radians
     */
    public float angle(final Vec2f o) {
        return (float) Math.acos( cosAngle(o) );
    }

    /**
     * Return the counter-clock-wise (CCW) normal of this vector, i.e. perp(endicular) vector
     */
    public Vec2f normal_ccw() {
        return new Vec2f(-y, x);
    }

    public boolean intersects(final Vec2f o) {
        if( Math.abs(x-o.x) >= FloatUtil.EPSILON || Math.abs(y-o.y) >= FloatUtil.EPSILON ) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return x + " / " + y;
    }
}
