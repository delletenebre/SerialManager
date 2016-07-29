package kg.delletenebre.serialmanager.helper;

public class Converter {
    private static final int HEX_RADIX = 16;

    public static String hex2dec(String hexStr) {
        long number = 0;
        int  digit;
        for (int i = 0; i < hexStr.length(); i++) {
            digit = Character.digit(hexStr.toLowerCase().charAt(i), HEX_RADIX);
            number += digit * Math.pow(HEX_RADIX, (hexStr.length() - 1 - i));
        }

        return String.valueOf(number);
    }

    public static String dex2hex(long decimal) {
        String hex = "";

        while (decimal != 0) {
            long hexValue = decimal % 16;
            char hexDigit = (hexValue <= 9 && hexValue >= 0) ?
                    (char)(hexValue + '0') : (char)(hexValue - 10 + 'A');
            hex = hexDigit + hex;
            decimal = decimal / 16;
        }

        return hex;
    }
}
