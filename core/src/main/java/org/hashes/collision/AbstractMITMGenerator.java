/**
 *    Copyright 2012 Pedro Ribeiro
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.hashes.collision;

import java.text.MessageFormat;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hashes.algorithm.HashAlgorithm;
import org.hashes.progress.ProgressMonitor;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;

/**
 * Base class of meet in the middle hash collision generator.
 * 
 * @author ribeirux
 * @version $Revision$
 */
public abstract class AbstractMITMGenerator extends AbstractCollisionGenerator {

    private static final Log LOG = LogFactory.getLog(AbstractMITMGenerator.class);

    private static final int LOOKUP_MAP_SIZE = (int) Math.pow(2, 18);

    private static final int LOOKUP_MAP_KEY_SIZE = 3;

    private static final int KEY_SIZE = 7;

    // inclusive
    private static final char START_KEY = ' ';

    // inclusive
    private static final char END_KEY = '~';

    private final String seed;

    private final int workerThreads;

    /**
     * Creates a new instance with specified hash algorithm and seed.
     * 
     * @param hashAlgorithm the hash algorithm
     * @param seed MITM seed
     */
    public AbstractMITMGenerator(final HashAlgorithm hashAlgorithm, final String seed) {
        this(hashAlgorithm, seed, null);
    }

    /**
     * Creates a new instance with specified hash algorithm and seed.
     * 
     * @param hashAlgorithm the hash algorithm
     * @param seed MITM seed
     * @param workerThreads number of worker threads, If null the number of available processors is used
     */
    public AbstractMITMGenerator(final HashAlgorithm hashAlgorithm, final String seed, final Integer workerThreads) {
        super(hashAlgorithm);
        this.seed = Preconditions.checkNotNull(seed, "seed");
        if (workerThreads == null) {
            this.workerThreads = Runtime.getRuntime().availableProcessors();
        } else {
            Preconditions.checkArgument(workerThreads > 0, "workerThreads");
            this.workerThreads = workerThreads;
        }
    }

    @Override
    protected List<String> generateNewCollisions(final int numberOfKeys, final ProgressMonitor monitor) {
        final int hash = this.getHashAlgorithm().hash(this.seed);
        final Map<Integer, String> lookupMap = this.createLookupMap(hash);
        final List<Callable<List<String>>> tasks = this.buildTasks(lookupMap, numberOfKeys, monitor);
        final ExecutorService executor = Executors.newFixedThreadPool(tasks.size());

        try {
            List<Future<List<String>>> results = Collections.emptyList();

            try {
                results = executor.invokeAll(tasks);
            } finally {
                executor.shutdown();
            }

            final Builder<String> collisions = ImmutableList.builder();
            for (final Future<List<String>> future : results) {
                collisions.addAll(future.get());
            }

            return collisions.build();
        } catch (final Exception e) {
            throw new ComputationException(e);
        }
    }

    private Map<Integer, String> createLookupMap(final int hash) {
        final Map<Integer, String> lookupMap = new HashMap<Integer, String>(LOOKUP_MAP_SIZE);
        for (int i = 0; i < LOOKUP_MAP_SIZE; i++) {
            final String sufix = this.randomString(LOOKUP_MAP_KEY_SIZE);
            lookupMap.put(this.hashBack(sufix, hash), sufix);
        }

        return Collections.unmodifiableMap(lookupMap);
    }

    private String randomString(final int size) {
        final StringBuilder random = new StringBuilder(size);
        for (int i = 0; i < size; i++) {
            random.append((char) (Math.random() * (END_KEY - START_KEY + 1) + START_KEY));
        }

        return String.valueOf(random);
    }

    private List<Callable<List<String>>> buildTasks(final Map<Integer, String> lookupMap, final int size,
            final ProgressMonitor monitor) {

        final int range = END_KEY - START_KEY + 1;
        final int maxWorkers = Math.min(range, this.workerThreads);

        if (maxWorkers != this.workerThreads && LOG.isWarnEnabled()) {
            LOG.warn(MessageFormat.format("The number of MITM worker threads is too high ({0}). Using: {1} threads",
                    this.workerThreads, maxWorkers));
        }

        final int interval = range / maxWorkers;
        final AtomicInteger keyCounter = new AtomicInteger();
        final Builder<Callable<List<String>>> tasks = ImmutableList.builder();
        for (int i = 0; i < maxWorkers; i++) {
            final char start = (char) (i * interval + START_KEY);
            final char end = (i == maxWorkers - 1 ? END_KEY : (char) (start + interval - 1));

            tasks.add(new MITMWorker(start, end, keyCounter, size, lookupMap, this.getHashAlgorithm(), monitor));
        }

        return tasks.build();
    }

    /**
     * Hash backwards.
     * 
     * @param key key to hash backwards
     * @param end final hash code
     * @return the backwards hash code
     */
    protected abstract int hashBack(final String key, final int end);

    /**
     * Meet in the middle hash collision worker.
     * 
     * @author ribeirux
     * @version $Revision$
     */
    private static final class MITMWorker implements Callable<List<String>> {

        private final char start;

        private final char end;

        private final AtomicInteger keyCounter;

        private final long maxNumberOfKeys;

        private final Map<Integer, String> lookupMap;

        private final HashAlgorithm hashAlgorithm;

        private final ProgressMonitor monitor;

        private MITMWorker(final char start, final char end, final AtomicInteger keyCounter, final int maxNumberOfKeys,
                final Map<Integer, String> lookupMap, final HashAlgorithm hashAlgorithm, final ProgressMonitor monitor) {
            this.start = start;
            this.end = end;
            this.keyCounter = keyCounter;
            this.maxNumberOfKeys = maxNumberOfKeys;
            this.lookupMap = lookupMap;
            this.hashAlgorithm = hashAlgorithm;
            this.monitor = monitor;
        }

        @Override
        public List<String> call() {
            final List<String> collisions = new LinkedList<String>();
            this.crack("", this.start, this.end, collisions);

            return collisions;
        }

        private void crack(final String prefix, final char startChar, final char endChar, final List<String> collisions) {
            if (prefix.length() == KEY_SIZE) {
                final String precomp = this.lookupMap.get(this.hashAlgorithm.hash(prefix));
                if (precomp != null) {
                    final int currentValue = this.keyCounter.getAndIncrement();
                    if (currentValue < this.maxNumberOfKeys) {
                        collisions.add(prefix + precomp);
                        this.monitor.update(currentValue);
                    }
                }
            } else {
                for (char i = startChar; (i <= endChar) && (this.maxNumberOfKeys > this.keyCounter.get()); i++) {
                    this.crack(prefix + i, START_KEY, END_KEY, collisions);
                }
            }
        }
    }
}
