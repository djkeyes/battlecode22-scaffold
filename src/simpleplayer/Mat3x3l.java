package simpleplayer;

public class Mat3x3l {
    long m00, m01, m02, m10, m11, m12, m20, m21, m22;

    public long get(int x, int y) {
        switch (x) {
            case 0:
                switch (y) {
                    case 0:
                        return m00;
                    case 1:
                        return m01;
                    case 2:
                        return m02;
                }
            case 1:
                switch (y) {
                    case 0:
                        return m10;
                    case 1:
                        return m11;
                    case 2:
                        return m12;
                }
            case 2:
                switch (y) {
                    case 0:
                        return m20;
                    case 1:
                        return m21;
                    case 2:
                        return m22;
                }
        }
        return -1;
    }

    public int numPositive() {
        return (m00 > 0 ? 1 : 0)
                + (m01 > 0 ? 1 : 0)
                + (m02 > 0 ? 1 : 0)
                + (m12 > 0 ? 1 : 0)
                + (m11 > 0 ? 1 : 0)
                + (m12 > 0 ? 1 : 0)
                + (m22 > 0 ? 1 : 0)
                + (m21 > 0 ? 1 : 0)
                + (m22 > 0 ? 1 : 0);
    }
}
