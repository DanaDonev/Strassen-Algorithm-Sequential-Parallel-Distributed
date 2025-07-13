interface HelpersDA {

    public static int[] flatten(int[][] matrix) {
        int n = matrix.length;
        int[] flat = new int[n * n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                flat[i * n + j] = matrix[i][j];
            }
        }
        return flat;
    }

    public static int[][] unflatten(int[] flat, int n) {
        int[][] matrix = new int[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                matrix[i][j] = flat[i * n + j];
            }
        }
        return matrix;
    }

}
