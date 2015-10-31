package edu.uci.ics.cs.gdtc.partitionloader;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;

import edu.uci.ics.cs.gdtc.partitiondata.AllPartitions;
import edu.uci.ics.cs.gdtc.partitiondata.LoadedPartitions;
import edu.uci.ics.cs.gdtc.partitiondata.LoadedVertexInterval;
import edu.uci.ics.cs.gdtc.partitiondata.PartitionQuerier;
import edu.uci.ics.cs.gdtc.partitiondata.Vertex;
import edu.uci.ics.cs.gdtc.support.Optimizers;

/**
 * This program loads partitions into the memory.
 * 
 * @author Aftab
 *
 */
public class PartitionLoader {

	private Vertex[] vertices = null;
	private ArrayList<LoadedVertexInterval> intervals = new ArrayList<LoadedVertexInterval>();
	private String baseFilename = "";
	private int numParts = 0;

	/**
	 * Initializes the partition loader, reads in the partition allocation
	 * table, and reads in the edgedestcountInfo
	 * 
	 * @param baseFilename
	 * @param numParts
	 * @throws NumberFormatException
	 * @throws IOException
	 */
	public PartitionLoader(String baseFilename, int numParts) throws NumberFormatException, IOException {
		this.baseFilename = baseFilename;
		this.numParts = numParts;

		// get the partition allocation table
		readPartAllocTable();
	}

	/**
	 * Loads the two partitions in the memory
	 * 
	 * @param partsToLoad
	 * @throws IOException
	 */
	public void loadParts(int[] partsToLoad) throws IOException {

		System.out.print("Loading partitions: ");
		for (int i = 0; i < partsToLoad.length; i++) {
			System.out.print(partsToLoad[i] + " ");
		}
		System.out.print("\n");

		// get the degrees of the source vertices in the partitions
		// getDegrees(baseFilename, partsToLoad);
		getLoadedPartDegs(baseFilename, partsToLoad);

		// initialize data structures of the partitions to load
		System.out.print("Initializing data structures for loading partitions... ");
		initPartDataStructs(partsToLoad);
		System.out.print("Done\n");

		// fill the partition data structures
		fillPartitionDataStructs(baseFilename, partsToLoad);
		System.out.println("Completed loading of partitions");

		int loadedPartOutDegrees[][] = LoadedPartitions.getLoadedPartOutDegs();
		int partEdgeArrays[][][] = LoadedPartitions.getLoadedPartEdges();
		byte partEdgeValArrays[][][] = LoadedPartitions.getLoadedPartEdgeVals();

		// sorting the partitions
		System.out.print("Sorting loaded partition data structures... ");
		for (int i = 0; i < partsToLoad.length; i++) {
			for (int j = 0; j < PartitionQuerier.getNumUniqueSrcs(partsToLoad[i]); j++) {
				int low = 0;
				int high = loadedPartOutDegrees[i][j] - 1;
				Optimizers.quickSort(partEdgeArrays[i][j], partEdgeValArrays[i][j], low, high);
			}
		}
		System.out.println("Done");

		LoadedPartitions.setLoadedParts(partsToLoad);

		/*
		 * TEST Loading of Partitions in Arrays
		 */
		System.out.println("Printing Loaded Partitions...");
		System.out.println("*****");
		for (int i = 0; i < partsToLoad.length; i++) {
			System.out.println("Partition: " + partsToLoad[i]);
			for (int j = 0; j < PartitionQuerier.getNumUniqueSrcs(partsToLoad[i]); j++) {
				int srcv = j + PartitionQuerier.getMinSrc(partsToLoad[i]);
				System.out.println("SourceV: " + srcv);
				System.out.println("Dest Vs: ");
				for (int k = 0; k < loadedPartOutDegrees[i][j]; k++) {
					System.out.print(partEdgeArrays[i][j][k] + " ");
				}
				System.out.println();
				System.out.println("Edge Vals: ");
				for (int k = 0; k < loadedPartOutDegrees[i][j]; k++) {
					System.out.print(partEdgeValArrays[i][j][k] + " ");
				}
				System.out.println();
			}
		}
		System.out.println("*****");
	}

	/**
	 * Gets the partition allocation table. (Should be called only during first
	 * load)
	 * 
	 * @throws NumberFormatException
	 * @throws IOException
	 */
	private void readPartAllocTable() throws NumberFormatException, IOException {

		// initialize partAllocTable variable
		int partAllocTable[] = new int[numParts];

		/*
		 * Scan the partition allocation table file
		 */
		BufferedReader inPartAllocTabStrm = new BufferedReader(
				new InputStreamReader(new FileInputStream(new File(baseFilename + ".partAllocTable"))));
		String ln;

		System.out.print("Reading partition allocation table file " + baseFilename + ".partAllocTable... ");
		int i = 0;
		while ((ln = inPartAllocTabStrm.readLine()) != null) {
			String tok = ln;
			// store partition allocation table in memory
			partAllocTable[i] = Integer.parseInt(tok);
			i++;
		}
		AllPartitions.setPartAllocTab(partAllocTable);
		inPartAllocTabStrm.close();
		System.out.println("Done");

	}

	public Vertex[] getVertices() {
		return vertices;
	}

	public ArrayList<LoadedVertexInterval> getIntervals() {
		return intervals;
	}

	/**
	 * Gets the degrees of the source vertices of the partitions that are to be
	 * loaded. (Deprecated- this method reads the degrees file of the entire
	 * graph.)
	 * 
	 * @param baseFilename
	 * @param partsToLoad
	 * @throws IOException
	 * @throws NumberFormatException
	 */
	private void getLoadedPartDegs(String baseFilename, int[] partsToLoad) throws IOException {

		/*
		 * Initialize the degrees array for each partition
		 */

		// initialize Dimension 1 (Total no. of Partitions)
		int[][] partOutDegs = new int[partsToLoad.length][];

		for (int i = 0; i < partsToLoad.length; i++) {

			// initialize Dimension 2 (Total no. of Unique SrcVs for a
			// Partition)
			partOutDegs[i] = new int[PartitionQuerier.getNumUniqueSrcs(partsToLoad[i])];
		}

		/*
		 * Scan degrees file of each partition
		 */
		for (int i = 0; i < partsToLoad.length; i++) {
			BufferedReader outDegInputStrm = new BufferedReader(new InputStreamReader(
					new FileInputStream(new File(baseFilename + ".partition." + partsToLoad[i] + ".degrees"))));

			System.out.print("Reading partition degrees file (" + baseFilename + ".partition." + partsToLoad[i]
					+ ".degrees) to obtain degrees of source vertices in partition " + partsToLoad[i] + "... ");

			String ln;
			while ((ln = outDegInputStrm.readLine()) != null) {

				String[] tok = ln.split("\t");

				// get the srcVId and degree
				int srcVId = Integer.parseInt(tok[0]);
				int deg = Integer.parseInt(tok[1]);

				partOutDegs[i][srcVId - PartitionQuerier.getMinSrc(partsToLoad[i])] = deg;
			}
			outDegInputStrm.close();
		}
		LoadedPartitions.setLoadedPartOutDegs(partOutDegs);
		System.out.println("Done");

	}

	/**
	 * Gets the degrees of the source vertices of the partitions that are to be
	 * loaded. (Deprecated- this method reads the degrees file of the entire
	 * graph.)
	 * 
	 * @param baseFilename
	 * @param partsToLoad
	 * @throws IOException
	 * @throws NumberFormatException
	 */
	@SuppressWarnings("unused")
	private void getDegrees(String baseFilename, int[] partsToLoad) throws NumberFormatException, IOException {

		/*
		 * Initialize the degrees array for each partition
		 */

		// initialize Dimension 1 (Total no. of Partitions)
		int[][] partOutDegs = new int[partsToLoad.length][];

		for (int i = 0; i < partsToLoad.length; i++) {

			// initialize Dimension 2 (Total no. of Unique SrcVs for a
			// Partition)
			partOutDegs[i] = new int[PartitionQuerier.getNumUniqueSrcs(partsToLoad[i])];
		}

		/*
		 * Scan the degrees file
		 */
		BufferedReader outDegInputStrm = new BufferedReader(
				new InputStreamReader(new FileInputStream(new File(baseFilename + ".degrees"))));

		System.out.print("Reading degrees file (" + baseFilename
				+ ".degrees) to obtain degrees of source vertices in partitions to load... ");

		String ln;
		while ((ln = outDegInputStrm.readLine()) != null) {

			String[] tok = ln.split("\t");

			// get the srcVId and degree
			int srcVId = Integer.parseInt(tok[0]);
			int deg = Integer.parseInt(tok[1]);

			for (int i = 0; i < partsToLoad.length; i++) {

				// check if the srcVId belongs to this partition
				if (!PartitionQuerier.inPartition(srcVId, partsToLoad[i])) {
					continue;
				} else {
					try {
						System.out.println("interesting");
						partOutDegs[i][srcVId - PartitionQuerier.getMinSrc(partsToLoad[i])] = deg;
					} catch (ArrayIndexOutOfBoundsException e) {
					}
				}
			}
		}
		LoadedPartitions.setLoadedPartOutDegs(partOutDegs);
		outDegInputStrm.close();
		System.out.println("Done");
	}

	/**
	 * Initializes data structures of the partitions to load
	 */
	private void initPartDataStructs(int[] partsToLoad) {

		int[][] partOutDegs = LoadedPartitions.getLoadedPartOutDegs();

		// initializing new data structures
		int totalNumVertices = 0;
		for (int i = 0; i < partsToLoad.length; i++) {
			totalNumVertices += PartitionQuerier.getNumUniqueSrcs(partsToLoad[i]);
		}
		vertices = new Vertex[totalNumVertices];

		// initialize Dimension 1 (Total no. of Partitions)
		int partEdges[][][] = new int[partsToLoad.length][][];
		byte partEdgeVals[][][] = new byte[partsToLoad.length][][];

		int count = 0;
		for (int i = 0; i < partsToLoad.length; i++) {

			// initialize Dimension 2 (Total no. of Unique SrcVs for a
			// Partition)
			partEdges[i] = new int[PartitionQuerier.getNumUniqueSrcs(partsToLoad[i])][];
			partEdgeVals[i] = new byte[PartitionQuerier.getNumUniqueSrcs(partsToLoad[i])][];

			for (int j = 0; j < PartitionQuerier.getNumUniqueSrcs(partsToLoad[i]); j++) {

				// initialize Dimension 3 (Total no. of Out-edges for a SrcV)
				partEdges[i][j] = new int[partOutDegs[i][j]];
				partEdgeVals[i][j] = new byte[partOutDegs[i][j]];

				int vertexId = PartitionQuerier.getActualIdFrmPartArrId(j, partsToLoad[i]);
				vertices[count++] = new Vertex(vertexId, partEdges[i][j], partEdgeVals[i][j]);

			}
		}

		LoadedPartitions.setLoadedPartEdges(partEdges);
		LoadedPartitions.setLoadedPartEdgeVals(partEdgeVals);

	}

	/**
	 * Reads the partition files and stores them in arrays
	 * 
	 * @param partInputStream
	 * @throws IOException
	 */
	private void fillPartitionDataStructs(String baseFilename, int[] partsToLoad) throws IOException {

		int[][][] partEdges = LoadedPartitions.getLoadedPartEdges();
		byte[][][] partEdgeVals = LoadedPartitions.getLoadedPartEdgeVals();

		int indexSt = 0;
		int indexEd = 0;

		for (int i = 0; i < partsToLoad.length; i++) {

			System.out.println("Reading partition file: " + baseFilename + ".partition." + partsToLoad[i]);
			DataInputStream partInStrm = new DataInputStream(
					new BufferedInputStream(new FileInputStream(baseFilename + ".partition." + partsToLoad[i])));

			// stores the position of last filled edge (destV) and the edge val
			// in partEdges and partEdgeVals for a source vertex
			// for a partition
			int[] lastAddedEdgePos = new int[PartitionQuerier.getNumUniqueSrcs(partsToLoad[i])];
			for (int j = 0; j < lastAddedEdgePos.length; j++) {
				lastAddedEdgePos[j] = -1;
			}

			while (partInStrm.available() != 0) {
				{
					try {
						// get srcVId
						int src = partInStrm.readInt();

						// get corresponding arraySrcVId of srcVId
						int arraySrcVId = src - PartitionQuerier.getMinSrc(partsToLoad[i]);

						// get count (number of destVs from srcV in the current
						// list
						// of the partition file)
						int count = partInStrm.readInt();

						// get dstVId & edgeVal and store them in the
						// corresponding
						// arrays
						for (int j = 0; j < count; j++) {

							// dstVId
							partEdges[i][arraySrcVId][lastAddedEdgePos[arraySrcVId] + 1] = partInStrm.readInt();

							// edgeVal
							partEdgeVals[i][arraySrcVId][lastAddedEdgePos[arraySrcVId] + 1] = partInStrm.readByte();

							// increment the last added position for this row
							lastAddedEdgePos[arraySrcVId]++;
						}

					} catch (Exception exception) {
						break;
					}
				}
			}

			int partitionId = partsToLoad[i];
			LoadedVertexInterval interval = new LoadedVertexInterval(PartitionQuerier.getMinSrc(partitionId),
					PartitionQuerier.getMaxSrc(partitionId), partitionId);
			interval.setIndexStart(indexSt);
			indexEd = indexEd + PartitionQuerier.getNumUniqueSrcs(partitionId) - 1;
			interval.setIndexEnd(indexEd);
			intervals.add(interval);
			indexSt = indexEd + 1;
			partInStrm.close();
		}
	}
}
