/**
 *
 */
package wosaic.algorithms;

import java.util.ArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import wosaic.utilities.Mosaic;
import wosaic.utilities.Pixel;
import wosaic.utilities.Parameters;

/**
 * Simple "brute force" algorithm. Each time we receive a new pixel, Check each
 * mosaic region and see if the new distance score is better than any existing
 * score, and replace it if it is. This algorithm can be very slow, but is also
 * easy to implement
 */
public class BruteForceAlgorithm extends AbstractAlgorithm {

    protected LinkedBlockingQueue<Pixel> PixelQueue;
    protected AtomicBoolean running;
    protected AtomicBoolean pluginFinished;
    private long TIMEOUT = 50;

    /**
     * Default constructor-- simple call our superclass constructor
     *
     * @param mos The mosaic to process and fill
     * @param colorMap Color data for the source pixels
     */
    public BruteForceAlgorithm(Mosaic mos, int[][][] colorMap) {
        super(mos, colorMap);
        PixelQueue = new LinkedBlockingQueue<Pixel>();
        running = new AtomicBoolean(false);
        pluginFinished = new AtomicBoolean(false);
    }

    /**
     * Process a new pixel received from a source plugin. In this algorithm, we
     * check it against every region in the mosaic and see if it is a better
     * fit.
     *
     * @param pixel The new source pixel
     */
    @Override
    public void AddPixel(Pixel pixel) {
        try {
            PixelQueue.put(pixel);
        } catch (InterruptedException ex) {
            // We've been canceled!
            pluginFinished.set(true);
            return;
        }

        // Start running if we're not
        boolean wasRunning = running.getAndSet(true);
        if (!wasRunning) {
            Thread workThread = new Thread(this, "AlgorithmThread");
            workThread.start();
        }
    }

    private void processPixel(Pixel pixel) {
//        final int[] avgColors = new int[3];
        for (int r = 0; r < Mos.getParams().resRows; r++) {
            for (int c = 0; c < Mos.getParams().resCols; c++) {

//                pixel.getAvgImageColor(avgColors);
                final int matchScore = getMatchScore(pixel, r, c);

//                Pixel[][] imageGrid = Mos.getPixelArr();
//                Parameters params = Mos.getParams();

                if (Mos.getPixelAt(r, c) == null) {
//                    updatePixel(Mos, pixel, imageGrid, params, r, c, matchScore);
                    Mos.UpdatePixel(r, c, pixel, matchScore);
                } else {

                    if (matchScore <= Mos.getScoreAt(r, c)) {
//                        updatePixel(Mos, pixel, imageGrid, params, r, c, matchScore);
                        Mos.UpdatePixel(r, c, pixel, matchScore);
                    }
                }
            }
        }
    }
    
//    private static void updatePixel(Mosaic mos, Pixel pixel, Pixel[][] imageGrid, Parameters params, int r, int c, int matchScore) {
//        boolean good = true;
//        for (int x = 1; x < 2; x++) {
//            for (int y = 1; y < 2; y++) {
//                if (!checkNeighbours(pixel, imageGrid, params, r, c, x, y)) {
//                    good = false;
//                    break;
//                }
//            }
//            if (!good) {
//                break;
//            }
//        }
//        if (good) {
//            mos.UpdatePixel(r, c, pixel, matchScore);
//        }
//    }


    private static boolean checkNeighbours(Pixel pixel, Pixel[][] imageGrid, Parameters params, int r, int c, int x, int y) {
        return (r - x >= 0 && r + x < params.resRows && c - y >= 0 && c + y < params.resCols
                && imageGrid[r - x][c] != pixel && imageGrid[r - x][c + y] != pixel
                && imageGrid[r - x][c - y] != pixel && imageGrid[r + x][c] != pixel
                && imageGrid[r][c + y] != pixel && imageGrid[r][c - y] != pixel
                && imageGrid[r + x][c - y] != pixel && imageGrid[r + x][c + y] != pixel);
    }

    /**
     * Consider a list of new Pixels that have been generated from the source
     * plugin. The default behavior is to simply process them one-by-one in
     * AddPixel(). However, new Algorithms are encouraged to override this as
     * appropriate
     *
     * @param pixels The new Pixel objects to consider
     */
    @Override
    public void AddPixels(ArrayList<Pixel> pixels) {
        if (pixels.isEmpty()) {
            return;
        }

        // Add the first pixel and get the program started if we haven't yet
        try {
            PixelQueue.put(pixels.remove(0));
        } catch (InterruptedException ex) {
            // We've been canceled!
            pluginFinished.set(true);
            return;
        }

        // Start running if we're not
        boolean wasRunning = running.getAndSet(true);
        if (!wasRunning) {
            Thread workThread = new Thread(this, "AlgorithmThread");
            workThread.start();
        }

        // Add the rest of the pixels
        for (Pixel pixel : pixels) {
            try {
                PixelQueue.put(pixel);
            } catch (InterruptedException ex) {
                // We've been canceled!
                pluginFinished.set(true);
                return;
            }
        }
    }

    public void run() {
        System.err.println("Algorithm thread running");

        boolean waitForMore = true;
        boolean finished = false;

        while (!Thread.interrupted() && !finished) {
            if (waitForMore) {
                waitForMore = !pluginFinished.get();
            }

            Pixel pixel = null;
            try {
                pixel = PixelQueue.poll(TIMEOUT, TimeUnit.MILLISECONDS);
            } catch (InterruptedException ex) {
                // We've been canceled!  Cleanup?
                waitForMore = false;
                pluginFinished.set(true);
            }

            if (pixel == null) {
                if (waitForMore) // Simply try again
                {
                    continue;
                } else {
                    finished = true;
                }

            } else // pixel != null
            {
                processPixel(pixel);
            }
        }

        System.err.println("Algorithm thread exiting..");
    }

    @Override
    public void FinishedAddingPixels() {
        System.err.println("Plugin notified algorithm that it's finished.");
        pluginFinished.set(true);
    }
}
