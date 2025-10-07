package com.yuqiangdede.common.util;

import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;
import org.apache.commons.math3.linear.SingularValueDecomposition;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDouble;
import org.opencv.core.Rect;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

/**
 * 图像对齐工具
 */
public class AlignUtil {

    /**
     * 人脸对齐
     *
     * @param image      图像数据
     * @param imagePoint 图像中的关键点
     * @param stdWidth   定义的标准图像的宽度
     * @param stdHeight  定义的标准图像的高度
     * @param stdPoint   定义的标准关键点
     */
    public static Mat alignedImage(Mat image, double[][] imagePoint, int stdWidth, int stdHeight, double[][] stdPoint) {
        Mat warp = null;
        Mat rectMat = null;
        try {
            warp = warpAffine(image, imagePoint, stdPoint);
            double imgWidth = warp.size().width;
            double imgHeight = warp.size().height;
            if (stdWidth <= imgWidth && stdHeight <= imgHeight) {
                return new Mat(warp, new Rect(0, 0, stdWidth, stdHeight));
            }
            int h;
            int w;
            if ((imgWidth / imgHeight) >= (1.0 * stdWidth / stdHeight)) {
                h = (int) Math.floor(imgHeight);
                w = (int) Math.floor(1.0 * stdWidth * imgHeight / stdHeight);

            } else {
                w = (int) Math.floor(imgWidth);
                h = (int) Math.floor(1.0 * stdHeight * imgWidth / stdWidth);
            }
            rectMat = new Mat(warp, new Rect(0, 0, w, h));
            Mat crop = new Mat();
            Imgproc.resize(rectMat, crop, new Size(stdWidth, stdHeight), 0, 0, Imgproc.INTER_NEAREST);
            return crop;
        } finally {
            if (null != rectMat) {
                rectMat.release();
            }
            if (null != warp) {
                warp.release();
            }
        }
    }

    /**
     * 图像仿射变换
     *
     * @param image    图像数据
     * @param imgPoint 图像中的关键点
     * @param stdPoint 定义的标准关键点
     * @return 图像的仿射结果图
     */
    public static Mat warpAffine(Mat image, double[][] imgPoint, double[][] stdPoint) {
        Mat matM = null;
        Mat matMTemp = null;
        try {
            RealMatrix imgPointMatrix = MathUtil.createMatrix(imgPoint);
            RealMatrix stdPointMatrix = MathUtil.createMatrix(stdPoint);
            int row = imgPointMatrix.getRowDimension();
            int col = imgPointMatrix.getColumnDimension();
            if (row <= 0 || col <= 0 || row != stdPointMatrix.getRowDimension() || col != stdPointMatrix.getColumnDimension()) {
                throw new RuntimeException("row or col is not equal");
            }
            RealVector imgPointMeanVector = MathUtil.mean(imgPointMatrix, 0);
            RealVector stdPointMeanVector = MathUtil.mean(stdPointMatrix, 0);
            RealMatrix imgPointMatrix1 = imgPointMatrix.subtract(MathUtil.createMatrix(row, imgPointMeanVector.toArray()));
            RealMatrix stdPointMatrix1 = stdPointMatrix.subtract(MathUtil.createMatrix(row, stdPointMeanVector.toArray()));
            double imgPointStd = MathUtil.std(imgPointMatrix1);
            double stdPointStd = MathUtil.std(stdPointMatrix1);
            RealMatrix imgPointMatrix2 = MathUtil.scalarDivision(imgPointMatrix1, imgPointStd);
            RealMatrix stdPointMatrix2 = MathUtil.scalarDivision(stdPointMatrix1, stdPointStd);
            RealMatrix pointsT = imgPointMatrix2.transpose().multiply(stdPointMatrix2);
            SingularValueDecomposition svdH = new SingularValueDecomposition(pointsT);
            RealMatrix U = svdH.getU();
            RealMatrix Vt = svdH.getVT();
            RealMatrix R = U.multiply(Vt).transpose();
            RealMatrix R1 = R.scalarMultiply(stdPointStd / imgPointStd);
            RealMatrix v21 = MathUtil.createMatrix(1, stdPointMeanVector.toArray()).transpose();
            RealMatrix v22 = R.multiply(MathUtil.createMatrix(1, imgPointMeanVector.toArray()).transpose());
            RealMatrix v23 = v22.scalarMultiply(stdPointStd / imgPointStd);
            RealMatrix R2 = v21.subtract(v23);
            RealMatrix M = MathUtil.hstack(R1, R2);
            matMTemp = new MatOfDouble(MathUtil.flatMatrix(M, 1).toArray());
            matM = new Mat(2, 3, CvType.CV_32FC3);
            matMTemp.reshape(1, 2).copyTo(matM);
            Mat dst = new Mat();
            Imgproc.warpAffine(image, dst, matM, image.size());
            return dst;
        } finally {
            if (null != matM) {
                matM.release();
            }
            if (null != matMTemp) {
                matMTemp.release();
            }
        }
    }

}
