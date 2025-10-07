package com.yuqiangdede.common.util;

import org.opencv.core.Mat;
import org.opencv.core.Range;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.Objects;

public class MatUtil {

    /**
     * 将Mat转换为BufferedImage
     * @return  BufferedImage
     */
    public static BufferedImage matToBufferedImage(Mat mat) {
        int dataSize = mat.cols() * mat.rows() * (int) mat.elemSize();
        byte[] data = new byte[dataSize];
        mat.get(0, 0, data);
        int type = mat.channels() == 1 ? BufferedImage.TYPE_BYTE_GRAY : BufferedImage.TYPE_3BYTE_BGR;
        if (type == BufferedImage.TYPE_3BYTE_BGR) {
            for (int i = 0; i < dataSize; i += 3) {
                byte blue = data[i];
                data[i] = data[i + 2];
                data[i + 2] = blue;
            }
        }
        BufferedImage image = new BufferedImage(mat.cols(), mat.rows(), type);
        image.getRaster().setDataElements(0, 0, mat.cols(), mat.rows(), data);
        return image;
    }

    /**
     * 将Mat转换为 Base64
     * @param mat
     * @return  Base64
     */
    public static String matToBase64(Mat mat) {
        ByteArrayOutputStream byteArrayOutputStream = null;
        try {
            byteArrayOutputStream = new ByteArrayOutputStream();
            ImageIO.write(matToBufferedImage(mat), "jpg", byteArrayOutputStream);
            byte[] bytes = byteArrayOutputStream.toByteArray();
            Base64.Encoder encoder = Base64.getMimeEncoder();
            return encoder.encodeToString(Objects.requireNonNull(bytes));
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            if (null != byteArrayOutputStream) {
                try {
                    byteArrayOutputStream.close();
                } catch (Exception ignored) {}
            }
        }
    }

    /**
     * 横向拼接两个图像的数据（Mat），该两个图像的类型必须是相同的类型，如：均为CvType.CV_8UC3类型
     * @author bailichun
     * @since 2020.02.20 15:00
     * @param m1 要合并的图像1（左图）
     * @param m2 要合并的图像2（右图）
     * @return 拼接好的Mat图像数据集。其高度等于两个图像中高度较大者的高度；其宽度等于两个图像的宽度之和。类型与两个输入图像相同。
     */
    public static Mat concat(Mat m1, Mat m2) {
        if (m1.type() != m2.type()) {
            throw new RuntimeException("concat:两个图像数据的类型不同！");
        }
        double w = m1.size().width + m2.size().width;
        double h = Math.max(m1.size().height, m2.size().height);
        Mat des = Mat.zeros((int) h, (int) w, m1.type());
        Mat rectForM1 = des.colRange(new Range(0, m1.cols()));
        int rowOffset1 = (int) (rectForM1.size().height - m1.rows()) / 2;
        rectForM1 = rectForM1.rowRange(rowOffset1, rowOffset1 + m1.rows());
        Mat rectForM2 = des.colRange(new Range(m1.cols(), des.cols()));
        int rowOffset2 = (int) (rectForM2.size().height - m2.rows()) / 2;
        rectForM2 = rectForM2.rowRange(rowOffset2, rowOffset2 + m2.rows());
        m1.copyTo(rectForM1);
        m2.copyTo(rectForM2);
        return des;
    }

}
