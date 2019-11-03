package logic;

/**
 *  Logic
 *  Grid: The state of the board
 */
public class Grid {
    private Box[][] grid;

    //constructor
    Grid(int gridSize, int boxSize) {
        createGrid(gridSize, boxSize);
    }

    //get box based on x and y
    public Box getBox(int x, int y) {
        return grid[x][y];
    }

    //create the initial grid
    private void createGrid(int gridSize, int boxSize) {
        grid = new Box[gridSize][gridSize];
        for(int i = 0; i < gridSize; i++) {
            for(int j = 0; j < gridSize; j++) {
                grid[i][j] = new Box(boxSize);
            }
        }
    }

    //get grid size
    int size() {
        return grid.length;
    }
}
