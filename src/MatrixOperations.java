import java.util.Random;

public class MatrixOperations {

    public static int[][] create(int n) {

        Random random = new Random();
        int[][] R = new int[n][n];

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                R[i][j] = random.nextInt(10) + 1;
            }
        }

        return R;
    }

    public static int[][] createIdentity(int n) {

        int[][] I = new int[n][n];

        for (int i = 0; i < n; i++) {
            I[i][i] = 1;
        }

        return I;
    }

    public static void printMatrix(int[][] P) {
        int n = P.length;
        for (int i = 0; i < n; i++) {

            for (int j = 0; j < n; j++) {
                System.out.print(P[i][j] + " ");
            }

            System.out.println();
        }
    }

    public int[][] add(int[][] A, int[][] B) {
        int n = A.length;
        int[][] C = new int[n][n];

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                C[i][j] = A[i][j] + B[i][j];
            }
        }

        return C;
    }

    public int[][] subtract(int[][] A, int[][] B) {
        int n = A.length;
        int[][] C = new int[n][n];

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                C[i][j] = A[i][j] - B[i][j];
            }
        }

        return C;
    }

    public static int[][] multiply(int[][] A, int[][] B) {
        int n = A.length;
        int[][] C = new int[n][n];

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                for (int k = 0; k < n; k++) {
                    C[i][j] += A[i][k] * B[k][j];
                }
            }
        }

        return C;
    }

    public void extract(int[][] A, int[][] C, int i, int j) {

        for (int iC = 0; iC < C.length; iC++) {
            for (int jC = 0; jC < C.length; jC++) {
                C[iC][jC] = A[i + iC][j + jC];
            }
        }
    }

    public void join(int[][] C, int[][] A, int i, int j) {

        for (int iC = 0; iC < C.length; iC++) {
            for (int jC = 0; jC < C.length; jC++) {
                A[i + iC][j + jC] = C[iC][jC];
            }
        }
    }

}
