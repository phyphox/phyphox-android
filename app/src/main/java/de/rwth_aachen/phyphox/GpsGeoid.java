package de.rwth_aachen.phyphox;

//This is a simplified port of the geoid correction code found in the GeographicLib (https://geographiclib.sourceforge.io/)

import java.io.InputStream;

public class GpsGeoid {
    double offset = 0.0;
    double scale = 1.0;
    int width, height, maxval;
    double lonres, latres;

    //Cache
    int _ix, _iy;
    double v00, v01, v10, v11;
    byte[] data;

    GpsGeoid(InputStream geoidData) {
        String line;
        try {
            while (true) {
                line = "";
                int c = 0;
                while ((c = geoidData.read()) != '\n')
                    line += (char)c;
                if (line.isEmpty() || line.equals("P5"))
                    continue;
                if (line.charAt(0) == '#') {
                    if (line.substring(2, 8).equals("Offset"))
                        offset = Double.parseDouble(line.substring(9));
                    else if (line.substring(2, 7).equals("Scale"))
                        scale = Double.parseDouble(line.substring(8));
                } else {
                    String[] parts = line.split("\\s+");
                    width = Integer.parseInt(parts[0]);
                    height = Integer.parseInt(parts[1]);
                    break;
                }
            }
            line = "";
            int c = 0;
            while ((c = geoidData.read()) != '\n')
                line += (char)c;
            maxval = Integer.parseInt(line);

            data = new byte[geoidData.available()];
            geoidData.read(data);
        } catch (Exception e) {
            e.printStackTrace();
            //Should not happen as the geo data file is bundled with the app
        }


        lonres = width / 360.0;
        latres = (height - 1) / 180.0;

        _ix = width;
        _iy = height;
    }

    private int rawval(int ix, int iy) {
        if (ix < 0)
            ix += width;
        else if (ix >= width)
            ix -= width;

        int index = 2*(ix + width*iy);
        Integer upperByte = (int) data[index] & 0xff;
        Integer lowerByte = (int) data[index + 1] & 0xFF;
        return (upperByte << 8) + lowerByte;
    }

    public double height(double lat, double lon) {
        double fx =  lon * lonres,
               fy = -lat * latres;
        int ix = (int)Math.floor(fx),
            iy = (int)Math.min((height - 1)/2 - 1, Math.floor(fy));
        fx -= ix;
        fy -= iy;
        iy += (height - 1)/2;
        ix += ix < 0 ? width : (ix >= width ? -width : 0);

        if (!(ix == _ix && iy == _iy)) {
            v00 = rawval(ix    , iy    );
            v01 = rawval(ix + 1, iy    );
            v10 = rawval(ix    , iy + 1);
            v11 = rawval(ix + 1, iy + 1);
            _ix = ix;
            _iy = iy;
        }
        double
                a = (1 - fx) * v00 + fx * v01,
                b = (1 - fx) * v10 + fx * v11,
                c = (1 - fy) * a + fy * b,
                h = offset + scale * c;

        return h;
    }
}
