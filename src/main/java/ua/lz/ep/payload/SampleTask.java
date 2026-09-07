package ua.lz.ep.payload;



public class SampleTask implements Runnable {
    @Override
    public void run() {
        for (int i = 0; i < 10; i++) {
            if (Thread.currentThread().isInterrupted()) {
                // Clean up and exit early
                break;
            }
            try {
                Thread.sleep(1000); // Responds to interruption
            } catch (InterruptedException e) {
                // Restore the interrupted status
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
}
