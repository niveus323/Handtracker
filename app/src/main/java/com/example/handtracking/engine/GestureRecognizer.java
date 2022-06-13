package com.example.handtracking.engine;

import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.util.Log;

import com.google.mediapipe.solutions.hands.HandLandmark;
import com.google.mediapipe.solutions.hands.HandsResult;
import com.opencsv.CSVWriter;

import org.tensorflow.lite.Interpreter;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.locks.ReentrantLock;

public class GestureRecognizer {
    private final ArrayList<float[]> angleData = new ArrayList<>();
    private final ArrayList<float[]> prevData = new ArrayList<>();
    private final Queue<Gesture> gesturePredicted = new LinkedList<>();
    private Interpreter interpreter;
    public static final int SEQ_LENGTH = 10;
    public static final int ANGLE_LENGTH = 15;
    public static final int GESTURE_VARIATION = 7;
    public File filePath;
    public GestureRecognizer(AssetManager assetManager, File filePath) {
        try {
            interpreter = new Interpreter(loadModelFile(assetManager), null);
            this.filePath = filePath;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public enum Gesture {
        TAP,
        SLIDE,
//        SLIDE_OFF,
        DRAG,
//        DRAG_OFF,
        ZOOM_IN,
        ZOOM_OUT,
        VOLUME_UP,
        VOLUME_DOWN,
        NONE;
    }

    private ByteBuffer loadModelFile(AssetManager assetManager) throws IOException {
        AssetFileDescriptor assetFileDescriptor = assetManager.openFd("model.tflite");
        FileInputStream fileInputStream = new FileInputStream(assetFileDescriptor.getFileDescriptor());
        FileChannel fileChannel = fileInputStream.getChannel();
        long startOffset = assetFileDescriptor.getStartOffset();
        long length = assetFileDescriptor.getLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, length);
    }

    public Gesture recognizeGesture(HandsResult handsResult) {
        float[][] lmCoordinates = new float[HandLandmark.NUM_LANDMARKS][3];
        for(int i=0; i<HandLandmark.NUM_LANDMARKS; i++) {
            lmCoordinates[i][0] = handsResult.multiHandLandmarks().get(0).getLandmarkList().get(i).getX();
            lmCoordinates[i][1] = handsResult.multiHandLandmarks().get(0).getLandmarkList().get(i).getY();
            lmCoordinates[i][2] = handsResult.multiHandLandmarks().get(0).getLandmarkList().get(i).getZ();
        }

        float[][] a = new float[HandLandmark.NUM_LANDMARKS-3][3];
        float[][] b = new float[HandLandmark.NUM_LANDMARKS-3][3];
        a[0] = lmCoordinates[5];    b[0] = lmCoordinates[17];
        a[1] = lmCoordinates[1];    b[1] = lmCoordinates[2];
        a[2] = lmCoordinates[2];    b[2] = lmCoordinates[3];
        a[3] = lmCoordinates[3];    b[3] = lmCoordinates[4];
        a[4] = lmCoordinates[5];    b[4] = lmCoordinates[6];
        a[5] = lmCoordinates[6];    b[5] = lmCoordinates[7];
        a[6] = lmCoordinates[7];    b[6] = lmCoordinates[8];
        a[7] = lmCoordinates[9];    b[7] = lmCoordinates[10];
        a[8] = lmCoordinates[10];   b[8] = lmCoordinates[11];
        a[9] = lmCoordinates[11];   b[9] = lmCoordinates[12];
        a[10] = lmCoordinates[13];  b[10] = lmCoordinates[14];
        a[11] = lmCoordinates[14];  b[11] = lmCoordinates[15];
        a[12] = lmCoordinates[15];  b[12] = lmCoordinates[16];
        a[13] = lmCoordinates[17];  b[13] = lmCoordinates[18];
        a[14] = lmCoordinates[18];  b[14] = lmCoordinates[19];
        a[15] = lmCoordinates[19];  b[15] = lmCoordinates[20];
        a[16] = lmCoordinates[0];   b[16] = lmCoordinates[5];
        a[17] = lmCoordinates[0];   b[17] = lmCoordinates[17];

        double[][] v = new double[HandLandmark.NUM_LANDMARKS-3][3];
        for (int i=0; i<HandLandmark.NUM_LANDMARKS-3; i++) {
            v[i][0] = b[i][0] - a[i][0];
            v[i][1] = b[i][1] - a[i][1];
            v[i][2] = b[i][2] - a[i][2];
        }

        double[][] v_normal = new double[HandLandmark.NUM_LANDMARKS-3][3];
        for (int i=0; i<HandLandmark.NUM_LANDMARKS-3; i++){
            double sum = Math.sqrt(v[i][0]*v[i][0] + v[i][1]*v[i][1] + v[i][2]*v[i][2]);
            v_normal[i][0] = (double)v[i][0]/sum;
            v_normal[i][1] = (double)v[i][1]/sum;
            v_normal[i][2] = (double)v[i][2]/sum;
        }
        float[] angle = new float[24];
        angle[0] = arccos(0, 1, v_normal);angle[1] = arccos(1, 2, v_normal);
        angle[2] = arccos(2, 3, v_normal);angle[3] = arccos(0, 4, v_normal);
        angle[4] = arccos(4, 5, v_normal);angle[5] = arccos(5, 6, v_normal);
        angle[6] = arccos(0, 7, v_normal);angle[7] = arccos(7, 8, v_normal);
        angle[8] = arccos(8, 9, v_normal);angle[9] = arccos(0, 10, v_normal);
        angle[10] = arccos(10, 11, v_normal);angle[11] = arccos(11, 12, v_normal);
        angle[12] = arccos(0, 13, v_normal);angle[13] = arccos(13, 14, v_normal);
        angle[14] = arccos(14, 15, v_normal);angle[15] = arccos(3, 0, v_normal);
        angle[16] = arccos(6, 0, v_normal);angle[17] = arccos(9, 0, v_normal);
        angle[18] = arccos(12, 0, v_normal);angle[19] = arccos(15, 0, v_normal);
        angle[20] = arccos(6, 2, v_normal);angle[21] = arccos(9, 2, v_normal);
        angle[22] = arccos(12, 2, v_normal);angle[23] = arccos(15, 2, v_normal);
        synchronized (angleData) {
            angleData.add(angle);
            if(angleData.size() < SEQ_LENGTH) return Gesture.NONE;
            float[][] output = new float[1][GESTURE_VARIATION];
            float[][][] input = new float[1][SEQ_LENGTH][24];
            input[0] = angleData.subList(angleData.size()-SEQ_LENGTH, angleData.size()).toArray(new float[][]{ new float[24]});
            interpreter.run(input, output);
            int max = Gesture.NONE.ordinal();
            for(int i=0; i<GESTURE_VARIATION; i++) {
                if(output[0][i] >= 0.9999 && (max == Gesture.NONE.ordinal() || output[0][max] < output[0][i])) max = i;
            }
            gesturePredicted.offer(Gesture.values()[max]);
            if(gesturePredicted.size() > 3) gesturePredicted.poll();
            if(checkGestureRepeated(3)) return Gesture.values()[max];
        }
        return Gesture.NONE;
    }

    private boolean checkGestureRepeated(int count) {
        if(gesturePredicted.size() < count){
            return false;
        }
        Log.i("GESTURE RECOGNITION", gesturePredicted.toString());
        HashSet<Gesture> hashSet = new HashSet<>(gesturePredicted);
        return hashSet.size() <= 1;
    }

    private float arccos(int i, int j, double[][] v_normal) {
        return (float) Math.toDegrees(Math.acos(v_normal[i][0]*v_normal[j][0] + v_normal[i][1]*v_normal[j][1] + v_normal[i][2]*v_normal[j][2]));
    }

    public void endRecognition() {
        synchronized (prevData) {
            if(!prevData.isEmpty()) prevData.clear();
            prevData.addAll(angleData);
        }
        synchronized (angleData) {
            angleData.clear();
        }
    }

    public void writeData(Gesture type) {
        String filename = String.format("%s/%s.csv", filePath, type.toString());
        try {
            FileWriter fw = new FileWriter(filename);
            CSVWriter writer = new CSVWriter(fw);
            for(int i=0; i<prevData.size(); i++){
                float[] values = prevData.get(i);
                String[] entries = new String[ANGLE_LENGTH];
                for(int j=0; j<ANGLE_LENGTH; j++){
                    entries[j] = ""+values[j];
                }
                writer.writeNext(entries);
            }
            writer.close();

        }catch (IOException e) {
            e.printStackTrace();
        }
    }
}
