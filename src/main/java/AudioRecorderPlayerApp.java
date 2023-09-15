import javax.sound.sampled.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYSplineRenderer;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.jtransforms.fft.DoubleFFT_1D;

public class AudioRecorderPlayerApp {
    private static final int sampleRate = 32000;
    private static final int bitsPerSample = 16;
    private static final int bufferSize = 4096;

    private static AudioFormat audioFormat = new AudioFormat(sampleRate, bitsPerSample, 1, true, false);
    private static TargetDataLine targetDataLine;
    private static SourceDataLine sourceDataLine;
    private static ByteArrayOutputStream audioOutputStream;
    private static XYSeries audioDataSeries;
    private static XYSeries spectrumDataSeries;
    private static JPanel chartPanel;
    private static boolean isRecording = false;

    public static void main(String[] args) {
        JFrame frame = new JFrame("Audio Recorder/Player");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(800, 400);

        JPanel panel = new JPanel();
        JButton recordButton = new JButton("Запись");
        JButton playButton = new JButton("Воспроизвести");
        JButton showChartButton = new JButton("Показать частотный график");
        JButton showSpectrumButton = new JButton("Показать спектр");

        recordButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (!isRecording) {
                    startRecording();
                    recordButton.setText("Остановить запись");
                    playButton.setEnabled(false);
                    showChartButton.setEnabled(false);
                    showSpectrumButton.setEnabled(false);
                } else {
                    stopRecording();
                    recordButton.setText("Запись");
                    playButton.setEnabled(true);
                    showChartButton.setEnabled(true);
                    showSpectrumButton.setEnabled(true);
                }
            }
        });

        playButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                startPlaying();
                playButton.setEnabled(false);
                showChartButton.setEnabled(false);
                showSpectrumButton.setEnabled(false);
            }
        });

        showChartButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                showFrequencyChart();
            }
        });

        showSpectrumButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                showSpectrum();
            }
        });

        panel.add(recordButton);
        panel.add(playButton);
        panel.add(showChartButton);
        panel.add(showSpectrumButton);
        frame.add(panel);

        frame.setVisible(true);
    }

    private static void startRecording() {
        Thread recordingThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    targetDataLine = AudioSystem.getTargetDataLine(audioFormat);
                    targetDataLine.open(audioFormat);
                    targetDataLine.start();

                    isRecording = true;

                    audioOutputStream = new ByteArrayOutputStream();
                    audioDataSeries = new XYSeries("Audio Data");

                    byte[] buffer = new byte[bufferSize];
                    int bytesRead;

                    while (isRecording) {
                        bytesRead = targetDataLine.read(buffer, 0, buffer.length);
                        if (bytesRead > 0) {
                            short[] shortBuffer = new short[bytesRead / 2];
                             ByteBuffer.wrap(buffer, 0, bytesRead).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shortBuffer);

                            for (int i = 0; i < shortBuffer.length; i++) {
                                if (shortBuffer[i] >0) audioDataSeries.add(i, shortBuffer[i]);
                            }

                            ByteBuffer byteBuffer = ByteBuffer.allocate(bytesRead);
                            byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
                            for (short s : shortBuffer) {
                                byteBuffer.putShort(s);
                            }
                            audioOutputStream.write(byteBuffer.array(), 0, byteBuffer.position());
                        }
                    }

                    targetDataLine.close();
                } catch (LineUnavailableException e) {
                    e.printStackTrace();
                }
            }
        });

        recordingThread.start();
    }

    private static void stopRecording() {
        isRecording = false;
        try {
            audioOutputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void startPlaying() {
        Thread playingThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    byte[] audioData = audioOutputStream.toByteArray();
                    AudioInputStream audioInputStream = new AudioInputStream(
                            new ByteArrayInputStream(audioData),
                            audioFormat,
                            audioData.length / (bitsPerSample / 8)
                    );

                    sourceDataLine = AudioSystem.getSourceDataLine(audioFormat);
                    sourceDataLine.open(audioFormat);
                    sourceDataLine.start();

                    byte[] buffer = new byte[bufferSize];
                    int bytesRead;

                    while ((bytesRead = audioInputStream.read(buffer, 0, buffer.length)) != -1) {
                        sourceDataLine.write(buffer, 0, bytesRead);
                    }

                    sourceDataLine.drain();
                    sourceDataLine.close();
                } catch (LineUnavailableException | IOException e) {
                    e.printStackTrace();
                }
            }
        });

        playingThread.start();
    }

    private static void showFrequencyChart() {
        XYSeriesCollection dataset = new XYSeriesCollection(audioDataSeries);
        JFreeChart chart = ChartFactory.createXYLineChart(
                "Частотный график аудиосигнала",
                "Время",
                "Частота",
                dataset,
                PlotOrientation.VERTICAL,
                true,
                true,
                false
        );

        XYPlot plot = chart.getXYPlot();
        XYSplineRenderer renderer = new XYSplineRenderer();
        plot.setRenderer(renderer);

        chartPanel = new ChartPanel(chart);
        chartPanel.setPreferredSize(new Dimension(800, 400));

        JFrame chartFrame = new JFrame("Частотный график аудиосигнала");
        chartFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        chartFrame.getContentPane().add(chartPanel);
        chartFrame.pack();
        chartFrame.setVisible(true);
    }

    private static void showSpectrum() {
        double[] audioData = getAudioDataAsDouble();
        DoubleFFT_1D fft = new DoubleFFT_1D(audioData.length);
        fft.realForward(audioData);

        spectrumDataSeries = new XYSeries("Audio Data");
        double frequencyResolution = (double) sampleRate / audioData.length;

        for (int i = 0; i < audioData.length / 2; i++) {
            double magnitude = Math.sqrt(audioData[2 * i] * audioData[2 * i] + audioData[2 * i + 1] * audioData[2 * i + 1]);
            spectrumDataSeries.add(i * frequencyResolution, magnitude);
        }

        XYSeriesCollection dataset = new XYSeriesCollection(spectrumDataSeries);
        JFreeChart chart = ChartFactory.createXYLineChart(
                "Спектр аудиосигнала",
                "Время",
                "Частота",
                dataset,
                PlotOrientation.VERTICAL,
                true,
                true,
                false
        );

        XYPlot plot = chart.getXYPlot();
        XYSplineRenderer renderer = new XYSplineRenderer();
        plot.setRenderer(renderer);

        chartPanel = new ChartPanel(chart);
        chartPanel.setPreferredSize(new Dimension(800, 400));

        JFrame chartFrame = new JFrame("Спектр аудиосигнала");
        chartFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        chartFrame.getContentPane().add(chartPanel);
        chartFrame.pack();
        chartFrame.setVisible(true);
    }

    private static double[] getAudioDataAsDouble() {
        byte[] audioBytes = audioOutputStream.toByteArray();
        int shortCount = audioBytes.length / 2;
        double[] audioData = new double[shortCount];

        ByteBuffer byteBuffer = ByteBuffer.wrap(audioBytes);
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN);

        for (int i = 0; i < shortCount; i++) {
            audioData[i] = byteBuffer.getShort();
        }

        return audioData;
    }

}
