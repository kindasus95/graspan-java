package edu.uci.ics.cs.gdtc.engine;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.logging.Logger;

import edu.uci.ics.cs.gdtc.edgecomputation.EdgeComputation;
import edu.uci.ics.cs.gdtc.edgecomputation.GraphDTCNewEdgesList;
import edu.uci.ics.cs.gdtc.GraphDTCLogger;
import edu.uci.ics.cs.gdtc.GraphDTCVertex;


/**
 * @author Kai Wang
 *
 * Created by Oct 8, 2015
 */
public class GraphDTCEngine {
	private static final Logger logger = GraphDTCLogger.getLogger("graphdtc engine");
	private ExecutorService computationExecutor;
	private long nUpdates;
	
	public GraphDTCEngine() {
		
	}
	
	/**
	 * Description:
	 * @param:
	 * @return:
	 */
	public void run() {
		
		// get the num of processors
		int nThreads = 8;
        if (Runtime.getRuntime().availableProcessors() > nThreads) {
            nThreads = Runtime.getRuntime().availableProcessors();
        }
        
        computationExecutor = Executors.newFixedThreadPool(nThreads);
        
		int intervalEnd = 0;
		int intervalStart = 0;
		
		//TODO: get the num of vertices
		int nVertices = intervalEnd - intervalStart + 1;
		
		GraphDTCVertex[] verticesFrom = new GraphDTCVertex[nVertices];
		GraphDTCVertex[] verticesTo = new GraphDTCVertex[nVertices];
		GraphDTCNewEdgesList[] edgesLists = new GraphDTCNewEdgesList[nVertices];
		
		logger.info("Loading Partitions...");
		long t = System.currentTimeMillis();
		// 1. load partitions into memory
		loadPartitions(verticesFrom, verticesTo);
		logger.info("Load took: " + (System.currentTimeMillis() - t) + "ms");
		
		logger.info("Starting computation and edge addition...");
		t = System.currentTimeMillis();
		// 2. do computation and add edges
		doComputation(verticesFrom, verticesTo, edgesLists);
		logger.info("Computation and edge addition took: " + (System.currentTimeMillis() - t) + "ms");
		
		// 3. store partitions to disk
		storePartitions();
	}

	/**
	 * Description:
	 * @param:
	 * @return:
	 */
	private void storePartitions() {
		
	}

	/**
	 * Description:
	 * @param:
	 * @return:
	 */
	private void doComputation(final GraphDTCVertex[] verticesFrom, 
			final GraphDTCVertex[] verticesTo, 
			final GraphDTCNewEdgesList[] edgesLists) {
		if(verticesFrom == null || verticesFrom.length == 0)
			return;
		
		if(verticesTo == null || verticesTo.length == 0)
			return;
		
		// set readable index, for read and write concurrency
		// for current iteration, readable index points to the last new edge in the previous iteration
		// which is readable for the current iteration
		setReadableIndex(edgesLists);
		
		final GraphDTCVertex[] vertices = verticesFrom;
		final Object termationLock = new Object();
        final int chunkSize = 1 + vertices.length / 64;

        final int nWorkers = vertices.length / chunkSize + 1;
        final AtomicInteger countDown = new AtomicInteger(1 + nWorkers);
        final AtomicIntegerArray nNewEdges = new AtomicIntegerArray(verticesFrom.length);
        final AtomicBoolean terminateFlag = new AtomicBoolean(false);
        
        while(!terminateFlag.get()) {
        	
	        // Parallel updates
	        for(int id = 0; id < nWorkers; id++) {
	            final int currentId = id;
	            final int chunkStart = currentId * chunkSize;
	            final int chunkEnd = chunkStart + chunkSize;
	
	            computationExecutor.submit(new Runnable() {
	
	                public void run() {
	                    int threadUpdates = 0;
	
	                    try {
	                        int end = chunkEnd;
	                        if (end > vertices.length) 
	                        	end = vertices.length;
	                        
	                        for(int i = chunkStart; i < end; i++) {
	                            GraphDTCVertex vertex = vertices[i];
	                            GraphDTCNewEdgesList edgeList = edgesLists[i];
	                            if (vertex != null) {
	                            	threadUpdates++;
	                                execUpdate(vertex, verticesFrom, verticesTo, 
	                                		edgeList, edgesLists, nNewEdges, i);
	                            }
	                        }
	
	                    } catch (Exception e) {
	                        e.printStackTrace();
	                    } finally {
	                        int pending = countDown.decrementAndGet();
	                        synchronized (termationLock) {
	                            nUpdates += threadUpdates;
	                            if (pending == 0) {
	                            	termationLock.notifyAll();
	                            }
	                        }
	                    }
	                }
	
	            });
	        }
        
	        synchronized (termationLock) {
	            while(countDown.get() > 0) {
	                try {
	                	termationLock.wait(1500);
	                } catch (InterruptedException e) {
	                    e.printStackTrace();
	                }
	                
	                if (countDown.get() > 0) 
	                	logger.info("Waiting for execution to finish: countDown:" + countDown.get());
	            }
	        }
	        
	        int sum = 0;
	        for(int i = 0; i < nNewEdges.length(); i++) {
	        	sum += nNewEdges.get(i);
	        }
	        
	        if(sum == 0)
	        	terminateFlag.set(true);
        }
    }
	

	/**
	 * Description:
	 * @param:
	 * @return:
	 */
	private void setReadableIndex(GraphDTCNewEdgesList[] edgesList) {
		if(edgesList == null || edgesList.length == 0)
			return;
		
		for(int i = 0; i < edgesList.length; i++) {
			GraphDTCNewEdgesList list = edgesList[i];
			if(list == null)
				return;
			int size = list.getSize();
			if(size == 0)
				return;
			list.setReadableSize(size);
			int index = list.getIndex();
			list.setReadableIndex(index);
		}
	}

	/**
	 * Description:
	 * @param:
	 * @return:
	 */
	private void loadPartitions(GraphDTCVertex[] verticesFrom, GraphDTCVertex[] verticesTo) {
		
	}
	
	/**
	 * Description:
	 * @param:
	 * @return:
	 */
	private void execUpdate(GraphDTCVertex vertex,
			GraphDTCVertex[] verticesFrom,
			GraphDTCVertex[] verticesTo,
			GraphDTCNewEdgesList edgeList,
			GraphDTCNewEdgesList[] edgesLists, 
			AtomicIntegerArray nNewEdges, int arrayIndex) {
		
		EdgeComputation.execUpate(vertex, verticesFrom, verticesTo, 
				edgeList, edgesLists, nNewEdges, arrayIndex);
	}
	
}
