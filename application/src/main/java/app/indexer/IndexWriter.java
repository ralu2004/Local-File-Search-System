package app.indexer;

import app.model.ExtractedRecord;
import app.repository.FileWriteRepository;
import app.indexer.job.IndexingLiveProgress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;

/**
 * Consumer side of the Producer-Consumer indexing pipeline.
 * <p>
 * Runs on a dedicated thread, draining {@link ExtractedRecord} items from
 * a shared queue and committing them to the database in batches. Stops when
 * it receives the poison pill sentinel value.
 */
public class IndexWriter {

    private static final Logger log = LoggerFactory.getLogger(IndexWriter.class);

    private final FileWriteRepository writeRepository;
    private final int batchSize;

    /**
     * @param writeRepository repository used to persist extracted records
     * @param batchSize       number of records per database commit
     */
    public IndexWriter(FileWriteRepository writeRepository, int batchSize) {
        this.writeRepository = writeRepository;
        this.batchSize = batchSize;
    }

    /**
     * Starts the writer thread. The thread drains the queue until it receives
     * the poison pill, then flushes any remaining records and exits.
     *
     * @param queue      shared queue between readers and this writer
     * @param poisonPill sentinel value signalling the writer to stop
     * @param stats      shared indexing statistics updated as records are written
     * @param liveProgress optional live progress publisher, may be null
     * @return the started writer thread
     */
    public Thread start(BlockingQueue<ExtractedRecord> queue, ExtractedRecord poisonPill,
                        IndexingStats stats, IndexingLiveProgress liveProgress) {
        Thread writerThread = new Thread(() -> {
            List<ExtractedRecord> batch = new ArrayList<>(batchSize);
            try {
                while (true) {
                    ExtractedRecord record = queue.take();
                    if (record == poisonPill) {
                        break;
                    }
                    batch.add(record);
                    if (batch.size() >= batchSize) {
                        flush(batch, stats);
                        if (liveProgress != null) {
                            liveProgress.publish(stats.totalFiles.get(), stats.indexed.get(),
                                    stats.skipped.get(), stats.failed.get(), batch.size());
                        }
                    }
                }
                if (!batch.isEmpty()) {
                    flush(batch, stats);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Writer thread interrupted");
            }
        }, "indexer-writer");
        writerThread.start();
        return writerThread;
    }

    /**
     * Writes a batch to the database and updates stats.
     * Clears the batch regardless of success or failure.
     *
     * @param batch records to persist
     * @param stats shared statistics to update
     */
    private void flush(List<ExtractedRecord> batch, IndexingStats stats) {
        try {
            writeRepository.batchUpsert(batch);
            stats.indexed.addAndGet(batch.size());
        } catch (SQLException e) {
            stats.failed.addAndGet(batch.size());
            log.error("Batch write failed: {}", e.getMessage());
        }
        batch.clear();
    }
}