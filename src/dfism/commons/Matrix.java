package dfism.commons;

import java.util.List;
import java.util.Map;

import dfism.fpo.FPSubset;

public class Matrix {
	private int[][] matrix;
	private int dim;
	
	public Matrix(int dim){
		this.dim = dim;
		matrix = new int[dim][dim];
	}
	
	public void clear(){
		matrix = null;
	}

	public int[][] get2DArray(){
		return matrix;
	}
	
	public int get(int row, int col){
		return matrix[row][col];
	}
	
	public void set(int row, int col, int value){
		matrix[row][col] = value;
	}
	
	public void add(int row, int col, int amount){
		matrix[row][col] += amount;
	}
	
	/*public void show(){
		for(int i=0; i<dim; i++){
			for(int j=0; j<dim; j++){
				System.out.print(matrix[i][j] + " ");
			}
			System.out.println("");
		}
	}*/
	
	/**
	 * Sum pairs of symmetric elements through the diagonal, results are saved the upper-half portion 
	 */
	public void summaryByDiagonalFolding(){
		for(int i=0; i<dim; i++){
			for(int j=i+1; j<dim; j++){
				matrix[i][j] += matrix[j][i];
			}
		}
	}
	
	/**
	 * Sum two diagonal-upper-half portions of two matrixes, result in the called matrix
	 * @param m another matrix
	 */
	public void summaryMergeWithMatrix(Matrix m){
		int[][] m_array = m.get2DArray();
		for(int i=0; i<dim; i++){
			for(int j=i+1; j<dim; j++){
				matrix[i][j] += m_array[i][j];
			}
		}
	}
	
	/**
	 * Copy the above part of the matrix to the below part by the diagonal
	 */
	public void makeSymmetric(){
		for(int i=0; i<dim; i++){
			for(int j=i+1; j<dim; j++){
				matrix[j][i] = matrix[i][j];
			}
		}
	}
	
	/**
	 * After completing this function, frequent2Itemsets contains all frequent 2-itemsets.
	 * </br>NOTE: The order of items in the 2-itemsets are based on the order of 'index_item'  
	 * @param frequent2Itemsets
	 * @param index_item
	 * @param support_count
	 */
	public void filter_frequent2Itemsets(Map<String, Integer> frequent2Itemsets, 
										List<String> index_item, int support_count){
		for(int i=0; i<dim; i++){
			for(int j=i+1; j<dim; j++){
				if(matrix[i][j]>=support_count){
					frequent2Itemsets.put(index_item.get(i)+" "+index_item.get(j), matrix[i][j]);
				}
			}
		}
	}
	
	/**
	 * After completing this function, frequent2Itemsets contains all frequent 2-itemsets.
	 * </br>NOTE: The order of items in the 2-itemsets are based on the order of 'index_item'  
	 * @param hFrequent2Itemsets
	 * @param index_item
	 * @param support_count
	 */
	public void filter_hFrequent2Itemsets(Map<String, FPSubset> hFrequent2Itemsets, int support_count){
		for(int i=0; i<dim; i++){
			for(int j=i+1; j<dim; j++){
				if(matrix[i][j]>=support_count){
					hFrequent2Itemsets.put(i+" "+j, new FPSubset(matrix[i][j]));
				}
			}
		}
	}
}
