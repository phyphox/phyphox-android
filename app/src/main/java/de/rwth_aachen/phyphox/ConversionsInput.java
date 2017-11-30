package de.rwth_aachen.phyphox;


// The class holds public static functions which convert values from a byte array to a double value.
public class ConversionsInput {

    /* private helper functions */

    private static Integer uInt16LittleEndian (byte lower, byte upper) {
        Integer lowerByte = (int) lower & 0xFF;
        Integer upperByte = (int) upper & 0xFF;
        return ((upperByte << 8) + lowerByte);
    }

    private static Integer int16LittleEndian (byte lower, byte upper) {
        Integer lowerByte = (int) lower & 0xFF;
        Integer upperByte = (int) upper;
        return ((upperByte << 8) + lowerByte);
    }

    private static Integer uInt24LittleEndian(byte lower, byte medium, byte upper) {
        Integer lowerByte = (int) lower & 0xFF;
        Integer mediumByte = (int) medium & 0xFF;
        Integer upperByte = (int) upper & 0xFF;
        return ((upperByte << 16) + (mediumByte << 8) + lowerByte);
    }

    private static Integer int24LittleEndian(byte lower, byte medium, byte upper) {
        Integer lowerByte = (int) lower & 0xFF;
        Integer mediumByte = (int) medium & 0xFF;
        Integer upperByte = (int) upper;
        return ((upperByte << 16) + (mediumByte << 8) + lowerByte);
    }

    private static Long uInt32LittleEndian (byte lower, byte mLower, byte mUpper, byte upper) {
        Long lowerByte = (long) lower & 0xFF;
        Long mLowerByte = (long) mLower & 0xFF;
        Long mUpperByte = (long) mUpper & 0xFF;
        Long upperByte = (long) upper & 0xFF;
        return ((upperByte << 24) + (mUpperByte << 16) + (mLowerByte << 8) + lowerByte);
    }

    private static Integer int32LittleEndian (byte lower, byte mLower, byte mUpper, byte upper) {
        Integer lowerByte = (int) lower & 0xFF;
        Integer mLowerByte = (int) mLower & 0xFF;
        Integer mUpperByte = (int) mUpper & 0xFF;
        Integer upperByte = (int) upper;
        return ((upperByte << 24) + (mUpperByte << 16) + (mLowerByte << 8) + lowerByte);
    }



    /* common functions */

    public static double uInt16LittleEndian(byte[] data) {
        return uInt16LittleEndian(data[0], data[1]);
    }

    public static double int16LittleEndian (byte[] data) {
        return int16LittleEndian(data[0], data[1]);
    }

    public static double uInt24LittleEndian (byte[] data) {
        return uInt24LittleEndian(data[0], data[1], data[2]);
    }

    public static double int24LittleEndian (byte[] data) {
        return int24LittleEndian(data[0], data[1], data[2]);
    }

    public static double uInt32LittleEndian (byte[] data) {
        return uInt32LittleEndian(data[0], data[1], data[2], data[3]);
    }

    public static double int32LittleEndian (byte[] data) {
        return int32LittleEndian(data[0], data[1], data[2], data[3]);
    }

    public static double stringAsDouble (byte[] data) {
        String s = new String(data);
        return Double.parseDouble(s);
    }

    public static double firstByte (byte[] data) {
        return data[0];
    }


    /* specific functions for the TI CC2650 SensorTag */

    private static final float SCALE_LSB = 0.03125f; // constant value for the temperature sensor

    public static double ti_tmp_obj (byte[] data) {
        int it = (int)((uInt16LittleEndian(data[0], data[1])) >> 2);
        return ((float)(it)) * SCALE_LSB; // degrees Celsius
    }

    public static double ti_tmp_amb (byte[] data) {
        int it = (int)((uInt16LittleEndian(data[2], data[3])) >> 2);
        return ((float)(it)) * SCALE_LSB; // degrees Celsius
    }

    public static double ti_gyro_x (byte[] data) {
        return (int16LittleEndian(data[0], data[1]) * 1.0) / (65536 / 500.); // degrees/second
    }

    public static double ti_gyro_y (byte[] data) {
        return (int16LittleEndian(data[2], data[3]) * 1.0) / (65536 / 500.); // degrees/second
    }

    public static double ti_gyro_z (byte[] data) {
        return (int16LittleEndian(data[4], data[5]) * 1.0) / (65536 / 500.); // degrees/second
    }

    public static double ti_gyro_x_rad (byte[] data) {
        return Math.toRadians(ti_gyro_x(data)); // radians / second
    }

    public static double ti_gyro_y_rad (byte[] data) {
        return Math.toRadians(ti_gyro_y(data)); // radians / second
    }

    public static double ti_gyro_z_rad (byte[] data) {
        return Math.toRadians(ti_gyro_z(data)); // radians / second
    }

    /*
     * Accelerometer ranges in G
     * possible values: 2, 4, 8, 16
     * default: 16
     * at the moment, it is not possible to change the value of accRange.
     */
    public static int accRange = 16;

    public static double ti_acc_x (byte[] data) {
        float v = (float) ((int16LittleEndian(data[6], data[7]) * 1.0) / (32768/accRange));
        return v; // Gravity (G)
    }

    public static double ti_acc_y (byte[] data) {
        float v = (float) ((int16LittleEndian(data[8], data[9]) * 1.0) / (32768/accRange));
        return v; // Gravity (G)
    }

    public static double ti_acc_z (byte[] data) {
        float v = (float) ((int16LittleEndian(data[10], data[11]) * 1.0) / (32768/accRange));
        return v; // Gravity (G)
    }

    public static double ti_mag_x (byte[] data) {
        return 1.0f * int16LittleEndian(data[12], data[13]); // uT (micro Tesla)
    }

    public static double ti_mag_y (byte[] data) {
        return 1.0f * int16LittleEndian(data[14], data[15]); // uT (micro Tesla)
    }

    public static double ti_mag_z (byte[] data) {
        return 1.0f * int16LittleEndian(data[16], data[17]); // uT (micro Tesla)
    }

    public static double ti_hum_temp (byte[] data) {
        // convert to short instead of converting uint16_t to int16_t
        Integer rawTemp = (int)uInt16LittleEndian(data[0], data[1]).shortValue();
        return (float)((double) rawTemp / 65536.) * 165 - 40; // Celsius
    }

    public static double ti_hum_hum (byte[] data) {
        Integer rawHum = uInt16LittleEndian(data[2], data[3]);
        rawHum &= ~0x0003; // remove status bits
        return (float) ((double) rawHum / 65536.) * 100; // %RH
    }

    public static double ti_bmp_temp (byte[] data) {
        return uInt24LittleEndian(data[0], data[1], data[2]) / 100.0f; // Celsius
    }

    public static double ti_bmp_press (byte[] data) {
        return uInt24LittleEndian(data[3], data[4], data[5]) / 100.0f; // hPa
    }

    public static double ti_opt (byte[] data) {
        Integer rawOpt = uInt16LittleEndian(data[0], data[1]);
        int m = rawOpt & 0x0FFF;
        int e = (rawOpt & 0xF000) >> 12;
        e = (e == 0) ? 1 : 2 << (e-1);
        return (float) (m * (0.01 * e));
    }

    public static double ti_left_key (byte[] data) { // works only on notification
        return data[0];
    }

    public static double ti_right_key (byte[] data) { // works only on notification
        return data[1];
    }

    public static double ti_reed_relay (byte[] data) { // works only on notification
        return data[2];
    }


    /* specific functions for the BBC micro:bit */

    public static double mb_acc_x (byte[] data) {
        return (int16LittleEndian(data[0], data[1]) / 1000.); // milli-newtons
    }

    public static double mb_acc_y (byte[] data) {
        return (int16LittleEndian(data[2], data[3]) / 1000.); // milli-newtons
    }

    public static double mb_acc_z (byte[] data) {
        return (int16LittleEndian(data[4], data[5]) / 1000.); // milli-newtons
    }

    public static double mb_mag_x (byte[] data) {
        return int16LittleEndian(data[0], data[1]);
    }

    public static double mb_mag_y (byte[] data) {
        return int16LittleEndian(data[2], data[3]);
    }

    public static double mb_mag_z (byte[] data) {
        return int16LittleEndian(data[4], data[5]);
    }


}
