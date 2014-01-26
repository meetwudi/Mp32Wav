package org.mp3transform.alarm;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.StringTokenizer;

/**
 * This scheduler supports the Unix crontab format, with the following
 * exception: Special strings and names are not supported.
 */
public class Scheduler extends Thread {

    private static Scheduler instance;

    private Set<Job> jobs = Collections.synchronizedSet(new HashSet<Job>());

    public static class Job {
        final Task task;
        String when;
        long nextRun;
        BitSet minutes, hoursOfDay, daysOfMonth, months, daysOfWeek;

        Job(Task task) {
            this.task = task;
        }

        void setWhen(String when) throws IllegalArgumentException {
            this.when = when;
            StringTokenizer tokenizer = new StringTokenizer(when, " \t\r\n");
            try {
                minutes = parseToken(tokenizer.nextToken(), 0, 59);
                hoursOfDay = parseToken(tokenizer.nextToken(), 0, 23);
                daysOfMonth = parseToken(tokenizer.nextToken(), 1, 31);
                months = parseToken(tokenizer.nextToken(), 1, 12);
                daysOfWeek = parseToken(tokenizer.nextToken(), 0, 7);
                if (daysOfWeek != null && daysOfWeek.get(7)) {
                    daysOfWeek.set(0);
                }
            } catch (Exception e) {
                IllegalArgumentException ex = new IllegalArgumentException(
                        "Unexpected scheduler format: expected 'minute hour dayOfMonth month dayOfWeek' but got "
                                + when);
                ex.initCause(e);
                throw ex;
            }
        }

        boolean shouldRunThisDay(Calendar calendar) {
            // Calendar.MONTH is 0 based
            if (mismatch(months, calendar.get(Calendar.MONTH) + 1)) {
                return false;
            }
            int dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH);
            // Calendar.SUNDAY = 1, SATURDAY = 7
            int dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY;
            if (daysOfWeek != null && daysOfMonth != null) {
                // exception: if both days are restricted, either field may
                // match
                if (mismatch(daysOfMonth, dayOfMonth) && mismatch(daysOfWeek, dayOfWeek)) {
                    return false;
                }
            } else {
                if (mismatch(daysOfMonth, dayOfMonth) || mismatch(daysOfWeek, dayOfWeek)) {
                    return false;
                }
            }
            return true;
        }

        boolean shouldRun(Calendar calendar) {
            if (!shouldRunThisDay(calendar)) {
                return false;
            }
            if (mismatch(minutes, calendar.get(Calendar.MINUTE))) {
                return false;
            }
            if (mismatch(hoursOfDay, calendar.get(Calendar.HOUR_OF_DAY))) {
                return false;
            }
            return true;
        }

        private boolean mismatch(BitSet set, int value) {
            return set != null && !set.get(value);
        }

        void run() {
            task.execute();
        }

        public String getSamples(int count) {
            StringBuffer buff = new StringBuffer();
            Calendar calendar = Calendar.getInstance();
            long time = java.sql.Timestamp.valueOf("2008-01-01 00:00:00").getTime();
            for (int i = 0, hit = 0; hit < count && i < 20000; i++) {
                calendar.setTimeInMillis(time);
                if (!shouldRunThisDay(calendar)) {
                    time += 24 * 60 * 60 * 1000;
                    continue;
                }
                if (shouldRun(calendar)) {
                    if (hit > 0) {
                        buff.append("; ");
                    }
                    String s = new java.sql.Timestamp(time).toString();
                    s = s.substring(2, 16);
                    buff.append(s);
                    hit++;
                }
                time += 60 * 1000;
            }
            return buff.toString();
        }

        private BitSet parseToken(String token, int min, int max) {
            if ("*".equals(token)) {
                return null;
            }
            BitSet bits = new BitSet(max - min);
            parseToken(bits, token, min, max);
            return bits;
        }

        private void parseToken(BitSet bits, String token, int min, int max) {
            if (token.indexOf(',') >= 0) {
                StringTokenizer tokenizer = new StringTokenizer(token, ",");
                if (!tokenizer.hasMoreElements()) {
                    throw new IllegalArgumentException(token);
                }
                while (tokenizer.hasMoreElements()) {
                    parseToken(bits, tokenizer.nextToken(), min, max);
                }
                return;
            }
            int step = 1;
            int idx = token.indexOf('/');
            if (idx >= 0) {
                step = Integer.parseInt(token.substring(idx + 1));
                token = token.substring(0, idx);
                if (step <= 0) {
                    throw new IllegalArgumentException("/" + step);
                }
            }
            idx = token.indexOf('-');
            if (idx >= 0) {
                int start = Integer.parseInt(token.substring(0, idx));
                int end = Integer.parseInt(token.substring(idx + 1));
                if (start < min || end > max || end < start) {
                    throw new IllegalArgumentException(token + " (" + min + "-" + max + ")");
                }
                for (int i = start; i <= end; i += step) {
                    bits.set(i);
                }
            } else if ("*".equals(token)) {
                for (int i = min; i <= max; i += step) {
                    bits.set(i);
                }
            } else {
                int x = Integer.parseInt(token);
                if (x < min || x > max) {
                    throw new IllegalArgumentException(token + " (" + min + "-" + max + ")");
                }
                bits.set(x);
            }
        }
    }

    /**
     * Get the scheduler instance. This method creates a new instance if
     * required.
     *
     * @return
     */
    public static synchronized Scheduler getInstance() {
        if (instance == null) {
            instance = new Scheduler();
            instance.setDaemon(true);
            instance.setName("Scheduler");
            instance.start();
        }
        return instance;
    }

    /**
     * This method is automatically called.
     */
    public void run() {
        while (true) {
            long now = System.currentTimeMillis();
            int sleep = (int) ((60 * 1000) - (now % (60 * 1000)));
            try {
                Thread.sleep(sleep);
            } catch (InterruptedException e) {
                // ignore
            }
            if (jobs.size() == 0) {
                continue;
            }
            ArrayList<Job> list = new ArrayList<Job>(jobs);
            now = System.currentTimeMillis();
            Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(now);
            for (int i = 0; i < list.size(); i++) {
                Job job = list.get(i);
                if (job.shouldRun(calendar)) {
                    job.run();
                }
            }
        }
    }

    /**
     * Create a new job with the given properties.
     *
     * @param when when the task should be run
     * @param taks the task to execute
     * @return the job
     */
    public Job createJob(String when, Task task) {
        Job job = new Job(task);
        job.setWhen(when);
        return job;
    }

    /**
     * Schedule a job.
     *
     * @param job the job to schedule
     */
    public void schedule(Job job) {
        jobs.add(job);
    }

}
