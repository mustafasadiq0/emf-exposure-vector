package org.emfvector.app;

/** 3GPP channel-number to carrier-frequency conversion (downlink and uplink). Returns MHz, NaN if unknown. */
public final class Bands {
    private Bands() {}

    // band, F_DL_low, N_offs_DL, N_max, duplex (DL-UL, MHz; 0 = TDD)
    private static final double[][] LTE = {
            {1, 2110, 0, 599, 190}, {2, 1930, 600, 1199, 80}, {3, 1805, 1200, 1949, 95},
            {4, 2110, 1950, 2399, 400}, {5, 869, 2400, 2649, 45}, {7, 2620, 2750, 3449, 120},
            {8, 925, 3450, 3799, 45}, {12, 729, 5010, 5179, 30}, {13, 746, 5180, 5279, -31},
            {17, 734, 5730, 5849, 30}, {20, 791, 6150, 6449, -41}, {25, 1930, 8040, 8689, 80},
            {26, 859, 8690, 9039, 45}, {28, 758, 9210, 9659, 55}, {32, 1452, 9920, 10359, 0},
            {38, 2570, 37750, 38249, 0}, {39, 1880, 38250, 38649, 0}, {40, 2300, 38650, 39649, 0},
            {41, 2496, 39650, 41589, 0}, {42, 3400, 41590, 43589, 0}, {43, 3600, 43590, 45589, 0},
            {46, 5150, 46790, 54539, 0}, {48, 3550, 55240, 56739, 0}, {66, 2110, 66436, 67335, 400},
            {71, 617, 68586, 68935, -46}};

    public static final class Carrier {
        public double dlMHz = Double.NaN, ulMHz = Double.NaN;
        public String band = "?";
        public boolean tdd;
    }

    public static Carrier lte(int earfcn) {
        Carrier c = new Carrier();
        for (double[] b : LTE) {
            if (earfcn >= b[2] && earfcn <= b[3]) {
                c.dlMHz = b[1] + 0.1 * (earfcn - b[2]);
                c.tdd = b[4] == 0;
                c.ulMHz = c.dlMHz - b[4];
                c.band = "B" + (int) b[0];
                return c;
            }
        }
        return c;
    }

    public static Carrier nr(int arfcn) {
        Carrier c = new Carrier();
        if (arfcn < 0 || arfcn > 3279165) return c;
        double f;
        if (arfcn < 600000) f = 0.005 * arfcn;
        else if (arfcn < 2016667) f = 3000 + 0.015 * (arfcn - 600000);
        else f = 24250.08 + 0.06 * (arfcn - 2016667);
        c.dlMHz = f;
        c.ulMHz = f; // TDD assumption (n41/n77/n78/n79/FR2 dominate); FDD offset ignored, limits are flat there
        c.tdd = f > 2400;
        if (f >= 3300 && f <= 3800) c.band = "n78";
        else if (f >= 3300 && f <= 4200) c.band = "n77";
        else if (f >= 4400 && f <= 5000) c.band = "n79";
        else if (f >= 2496 && f <= 2690) c.band = "n41";
        else if (f >= 24250) c.band = "FR2 mmWave";
        else c.band = "FR1";
        return c;
    }

    public static Carrier gsm(int arfcn) {
        Carrier c = new Carrier();
        if (arfcn >= 0 && arfcn <= 124) { c.dlMHz = 935 + 0.2 * arfcn; c.ulMHz = c.dlMHz - 45; c.band = "GSM900"; }
        else if (arfcn >= 975 && arfcn <= 1023) { c.dlMHz = 935 + 0.2 * (arfcn - 1024); c.ulMHz = c.dlMHz - 45; c.band = "E-GSM900"; }
        else if (arfcn >= 512 && arfcn <= 885) { c.dlMHz = 1805.2 + 0.2 * (arfcn - 512); c.ulMHz = c.dlMHz - 95; c.band = "DCS1800"; }
        else if (arfcn >= 128 && arfcn <= 251) { c.dlMHz = 869.2 + 0.2 * (arfcn - 128); c.ulMHz = c.dlMHz - 45; c.band = "GSM850"; }
        return c;
    }

    public static Carrier umts(int uarfcn) {
        Carrier c = new Carrier();
        if (uarfcn >= 2937 && uarfcn <= 3088) { c.dlMHz = 340 + 0.2 * uarfcn; c.ulMHz = c.dlMHz - 45; c.band = "UMTS B8"; }
        else if (uarfcn >= 10562 && uarfcn <= 10838) { c.dlMHz = 0.2 * uarfcn; c.ulMHz = c.dlMHz - 190; c.band = "UMTS B1"; }
        else if (uarfcn > 0 && uarfcn < 16384) { c.dlMHz = 0.2 * uarfcn; c.ulMHz = c.dlMHz; c.band = "UMTS"; }
        return c;
    }

    /** Default carrier when the channel number is hidden by the OS. */
    public static Carrier fallback(String tech) {
        Carrier c = new Carrier();
        switch (tech) {
            case "GSM": c.dlMHz = 942; c.ulMHz = 897; c.band = "assumed 900"; break;
            case "UMTS": c.dlMHz = 2140; c.ulMHz = 1950; c.band = "assumed B1"; break;
            case "NR": c.dlMHz = 3500; c.ulMHz = 3500; c.band = "assumed n78"; c.tdd = true; break;
            default: c.dlMHz = 1842; c.ulMHz = 1747; c.band = "assumed B3"; break;
        }
        return c;
    }
}
