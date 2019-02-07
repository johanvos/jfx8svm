/*
 * Copyright (c) 2013, 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package com.sun.javafx.font.coretext;

import com.sun.javafx.font.FontResource;
import com.sun.javafx.font.Glyph;
import com.sun.javafx.geom.RectBounds;
import com.sun.javafx.geom.Shape;

class CTGlyph implements Glyph {
    private CTFontStrike strike;
    private int glyphCode;
    private CGRect bounds;
    private double xAdvance;
    private double yAdvance;
    private boolean drawShapes;

    /* Always using BRGA context has the same performance as gray */
    private static boolean LCD_CONTEXT = true;
    private static boolean CACHE_CONTEXT = true;

    private static long cachedContextRef;
    private static final int BITMAP_WIDTH = 256;
    private static final int BITMAP_HEIGHT = 256;
    private static final int MAX_SIZE = 320;
    // private static final long GRAY_COLORSPACE = OS.CGColorSpaceCreateDeviceGray();
    // private static final long RGB_COLORSPACE = OS.CGColorSpaceCreateDeviceRGB();

    CTGlyph(CTFontStrike strike, int glyphCode, boolean drawShapes) {
        this.strike = strike;
        this.glyphCode = glyphCode;
        this.drawShapes = drawShapes;
    }

    @Override public int getGlyphCode() {
        return glyphCode;
    }

    /* Note, according to javadoc these bounds should be
     * in user space but T2K uses device space.
     */
    @Override public RectBounds getBBox() {
        /* IN T2k this is the bounds of the glyph path see GeneralPath.cpp */
        CGRect rect = strike.getBBox(glyphCode);
        if (rect == null) return new RectBounds();
        return new RectBounds((float)rect.origin.x,
                              (float)rect.origin.y,
                              (float)(rect.origin.x + rect.size.width),
                              (float)(rect.origin.y + rect.size.height));
    }

    private void checkBounds() {
System.err.println("[JVDBG] in java, checkBounds0\n");
        if (bounds != null) return;
        bounds = new CGRect();
        if (strike.getSize() == 0) return;

        long fontRef = strike.getFontRef();
        if (fontRef == 0) return;
        int orientation = OS.kCTFontOrientationDefault;
        CGSize size = new CGSize();
        OS.CTFontGetAdvancesForGlyphs(fontRef, orientation, (short)glyphCode, size);
        xAdvance = size.width;
        yAdvance = -size.height;   /*Inverted coordinates system */

        if (drawShapes) return;

        /* Avoid CTFontGetBoundingRectsForGlyphs as it is too slow */
//        bounds = OS.CTFontGetBoundingRectsForGlyphs(fontRef, orientation, (short)glyphCode, null, 1);

        CTFontFile fr = (CTFontFile)strike.getFontResource();
        float[] bb = new float[4];
        fr.getGlyphBoundingBox((short)glyphCode, strike.getSize(), bb);
        bounds.origin.x = bb[0];
        bounds.origin.y = bb[1];
        bounds.size.width = (bb[2] - bb[0]);
        bounds.size.height = (bb[3] - bb[1]);
        if (strike.matrix != null) {
Thread.dumpStack();
System.err.println("[JVDBG] in java, checkBounds1\n");
            /* Need to use the native matrix as it is y up */
            OS.CGRectApplyAffineTransform(bounds, strike.matrix);
System.err.println("[JVDBG] in java, checkBounds2\n");
        }

        if (bounds.size.width < 0 || bounds.size.height < 0 ||
            bounds.size.width > MAX_SIZE || bounds.size.height > MAX_SIZE) {
            /* Negative values for dimensions can indicate the font is corrupted.
             * Overly large dimensions also indicate problem with the font as
             * JavaFX uses path rasterizers for fontSize greater than 80pt.
             */
            bounds.origin.x = bounds.origin.y = bounds.size.width = bounds.size.height = 0;
        } else {

            /* The box is increased to capture all fragments from LCD rendering  */
            bounds.origin.x = (int)Math.floor(bounds.origin.x) - 1;
            bounds.origin.y = (int)Math.floor(bounds.origin.y) - 1;
            bounds.size.width = (int)Math.ceil(bounds.size.width) + 1 + 1 + 1;
            bounds.size.height = (int)Math.ceil(bounds.size.height) + 1 + 1 + 1;
        }
System.err.println("[JVDBG] in java, checkBounds3\n");
    }

    @Override public Shape getShape() {
        return strike.createGlyphOutline(glyphCode);
    }

    private long createContext(boolean lcd, int width, int height) {
System.err.println("[JVDBG] createcontext, lcd = "+lcd+", width = "+width+", height = "+height);
        long space;
        int bpc = 8, bpr, flags;
        if (lcd) {
            space = OS.CGColorSpaceCreateDeviceRGB();
            bpr = width * 4;
            flags = OS.kCGBitmapByteOrder32Host | OS.kCGImageAlphaPremultipliedFirst;
        } else {
            space = OS.CGColorSpaceCreateDeviceGray();
            bpr = width;
            flags = OS.kCGImageAlphaNone;
        }
System.err.println("[JVDBG] createcontext 0");
        long context =  OS.CGBitmapContextCreate(0, width, height, bpc, bpr, space, flags);
System.err.println("[JVDBG] createcontext 1");

        boolean subPixel = strike.isSubPixelGlyph();
System.err.println("[JVDBG] createcontext 2");
        OS.CGContextSetAllowsFontSmoothing(context, lcd);
System.err.println("[JVDBG] createcontext 3");
        OS.CGContextSetAllowsAntialiasing(context, true);
System.err.println("[JVDBG] createcontext 4");
        OS.CGContextSetAllowsFontSubpixelPositioning(context, subPixel);
System.err.println("[JVDBG] createcontext 5");
        OS.CGContextSetAllowsFontSubpixelQuantization(context, subPixel);
System.err.println("[JVDBG] createcontext 6");
        return context;

    }

    private long getCachedContext(boolean lcd) {
        if (cachedContextRef == 0) {
            cachedContextRef = createContext(lcd, BITMAP_WIDTH, BITMAP_HEIGHT);
        }
        return cachedContextRef;
    }

    private synchronized byte[] getImage(double x, double y, int w, int h, int subPixel) {
System.err.println("[JVDBG] CTGlyph, getImage0");

        if (w == 0 || h == 0) return new byte[0];
System.err.println("[JVDBG] CTGlyph, getImage1");

        long fontRef = strike.getFontRef();
System.err.println("[JVDBG] CTGlyph, getImage2");
        boolean lcd = isLCDGlyph();
System.err.println("[JVDBG] CTGlyph, getImage3");
        boolean lcdContext = LCD_CONTEXT || lcd;
        CGAffineTransform matrix = strike.matrix;
        boolean cache = CACHE_CONTEXT & BITMAP_WIDTH >= w & BITMAP_HEIGHT >= h;
System.err.println("[JVDBG] CTGlyph, getImage4");
        long context = cache ? getCachedContext(lcdContext) :
                               createContext(lcdContext, w, h);
        if (context == 0) return new byte[0];
System.err.println("[JVDBG] CTGlyph, getImage5");

        /* Fill background with white */
System.err.println("[JVDBG] CTGlyph, getImage6");
        OS.CGContextSetRGBFillColor(context, 1, 1, 1, 1);
System.err.println("[JVDBG] CTGlyph, getImage7");
        CGRect rect = new CGRect();
        rect.size.width = w;
        rect.size.height = h;
System.err.println("[JVDBG] CTGlyph, getImage8");
        OS.CGContextFillRect(context, rect);
System.err.println("[JVDBG] CTGlyph, getImage9");

        double drawX = 0, drawY = 0;
        if (matrix != null) {
System.err.println("[JVDBG] CTGlyph, getImage10");
            OS.CGContextTranslateCTM(context, -x, -y);
System.err.println("[JVDBG] CTGlyph, getImage11");
        } else {
System.err.println("[JVDBG] CTGlyph, getImage12");
            drawX = x - strike.getSubPixelPosition(subPixel);
System.err.println("[JVDBG] CTGlyph, getImage13");
            drawY = y;
        }

        /* Draw the text with black */
System.err.println("[JVDBG] CTGlyph, getImage14");
        OS.CGContextSetRGBFillColor(context, 0, 0, 0, 1);
System.err.println("[JVDBG] CTGlyph, getImage15");
        OS.CTFontDrawGlyphs(fontRef, (short)glyphCode, -drawX, -drawY, context);
System.err.println("[JVDBG] CTGlyph, getImage16");

        if (matrix != null) {
System.err.println("[JVDBG] CTGlyph, getImage17");
            OS.CGContextTranslateCTM(context, x, y);
        }

        byte[] imageData;
        if (lcd) {
System.err.println("[JVDBG] CTGlyph, getImage18");
            imageData = OS.CGBitmapContextGetData(context, w, h, 24);
        } else {
System.err.println("[JVDBG] CTGlyph, getImage19");
            imageData = OS.CGBitmapContextGetData(context, w, h, 8);
        }
        if (imageData == null) {
System.err.println("[JVDBG] CTGlyph, getImage20");
            bounds = new CGRect();
            imageData = new byte[0];
        }

        if (!cache) {
System.err.println("[JVDBG] CTGlyph, getImage21");
            OS.CGContextRelease(context);
        }
System.err.println("[JVDBG] CTGlyph, getImage22");
        return imageData;
    }

    @Override public byte[] getPixelData() {
        return getPixelData(0);
    }

    @Override public byte[] getPixelData(int subPixel) {
System.err.println("[JVDBG] ctglyph, getPixelData0");
        checkBounds();
System.err.println("[JVDBG] ctglyph, getPixelData1");
        byte[] answer = getImage(bounds.origin.x, bounds.origin.y,
                        (int)bounds.size.width, (int)bounds.size.height, subPixel);
System.err.println("[JVDBG] ctglyph, getPixelData2");
        return answer;
    }

    @Override public float getAdvance() {
        checkBounds();
        //TODO should be user space (this method is not used)
        return (float)xAdvance;
    }

    @Override public float getPixelXAdvance() {
        checkBounds();
        return (float)xAdvance;
    }

    @Override public float getPixelYAdvance() {
        checkBounds();
        return (float)yAdvance;
    }

    @Override public int getWidth() {
        checkBounds();
        int w = (int)bounds.size.width;
        return isLCDGlyph() ? w * 3 : w;
    }

    @Override public int getHeight() {
        checkBounds();
        return (int)bounds.size.height;
    }

    @Override public int getOriginX() {
        checkBounds();
        return (int)bounds.origin.x;
    }

    @Override public int getOriginY() {
        checkBounds();
        int h = (int)bounds.size.height;
        int y = (int)bounds.origin.y;
        return -h - y; /*Inverted coordinates system */
    }

    @Override public boolean isLCDGlyph() {
        return strike.getAAMode() == FontResource.AA_LCD;
    }

}
