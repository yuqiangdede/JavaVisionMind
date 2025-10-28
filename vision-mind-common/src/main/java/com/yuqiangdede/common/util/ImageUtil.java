package com.yuqiangdede.common.util;


import java.awt.BasicStroke;
import java.awt.Color;
import static java.awt.Color.BLUE;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.GeneralPath;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.ComponentSampleModel;
import java.awt.image.DataBufferByte;
import java.awt.image.Raster;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import javax.imageio.ImageIO;

import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import com.yuqiangdede.common.dto.Point;
import com.yuqiangdede.common.dto.output.Box;
import com.yuqiangdede.common.dto.output.BoxWithKeypoints;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ImageUtil {

    /**
     * 将给定URL转换为ImageResult对象。
     *
     * @param urlStr 图片的URL地址
     * @return BufferedImage
     * @throws IOException 如果无法从URL读取图片，则抛出IOException异常
     */
    public static BufferedImage urlToImage(String urlStr) throws IOException {
        URL url = URI.create(urlStr).toURL();

        // 使用默认的 HttpsURLConnection
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");

        // 尝试通过url的Content-Type来获取图片格式
        String contentType = connection.getHeaderField("Content-Type");
        if (contentType == null) {
            throw new IOException("Img Url Has Sth. Wrong.");
        }

        // 下载图片流
        try (InputStream in = connection.getInputStream()) {
            BufferedImage bufferedImage = ImageIO.read(in);
            if (bufferedImage == null) {
                throw new IOException("Unsupported image format or corrupted image data.");
            }
            return bufferedImage;
        } finally {
            connection.disconnect(); // 确保连接被关闭
        }
    }

    /**
     * 从指定URL下载图片并将其转换为OpenCV的Mat对象
     *
     * @param urlStr 图片的URL地址
     * @return 返回OpenCV的Mat对象，表示下载并转换后的图片
     * @throws IOException 当无法建立到URL的连接、读取URL数据或无法读取图片时抛出此异常
     */
    public static Mat urlToMat(String urlStr) throws IOException {
        BufferedImage imageResult = urlToImage(urlStr);
        // 将字节数组转换为OpenCV的Mat对象
        return imgToMat(imageResult);
    }

    /**
     * 将Java BufferedImage对象转换为OpenCV Mat对象
     *
     * @param image 要转换的Java BufferedImage对象
     * @return 转换后的OpenCV Mat对象
     * @throws IOException 如果在转换过程中发生IO异常
     */
    public static Mat imgToMat(BufferedImage image) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        log.trace("image type: {}", checkColorType(image));
        ImageIO.write(image, "jpg", byteArrayOutputStream);

        byte[] bytes = byteArrayOutputStream.toByteArray();
        // 将字节数组转换为OpenCV的Mat对象
        return Imgcodecs.imdecode(new MatOfByte(bytes), Imgcodecs.IMREAD_COLOR);
    }

    public static BufferedImage matToBufferedImage(Mat mat) {
        if (mat == null || mat.empty()) {
            throw new IllegalArgumentException("Mat is null or empty");
        }

        Mat working = mat;
        if (mat.channels() == 4) {
            Mat converted = new Mat();
            Imgproc.cvtColor(mat, converted, Imgproc.COLOR_BGRA2BGR);
            working = converted;
        }

        int type = switch (working.channels()) {
            case 1 -> BufferedImage.TYPE_BYTE_GRAY;
            case 3 -> BufferedImage.TYPE_3BYTE_BGR;
            default -> throw new IllegalArgumentException("Unsupported Mat channel count: " + working.channels());
        };

        BufferedImage bufferedImage = new BufferedImage(working.cols(), working.rows(), type);
        byte[] data = ((DataBufferByte) bufferedImage.getRaster().getDataBuffer()).getData();
        working.get(0, 0, data);

        if (working != mat) {
            working.release();
        }
        return bufferedImage;
    }

    public static String urlToBase64( String urlStr) throws IOException {
        BufferedImage image = urlToImage(urlStr);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", baos);
        byte[] bytes = baos.toByteArray();
        return Base64.getEncoder().encodeToString(bytes);
    }

    /**
     * 检查BufferedImage对象的颜色类型
     *
     * @param image 要检查的BufferedImage对象
     * @return 返回BufferedImage对象的颜色类型，包括"RGB", "BGR", "RGBA", "BGRA"和"Unknown or Other Type"
     */
    private static String checkColorType(BufferedImage image) {
        Raster raster = image.getRaster();
        ComponentSampleModel sampleModel = (ComponentSampleModel) raster.getSampleModel();

        int[] bandOffsets = sampleModel.getBandOffsets(); // 正确获取通道偏移

        // 检查是否是BGR或RGB
        if (bandOffsets.length == 3) {
            if (bandOffsets[0] == 0 && bandOffsets[1] == 1 && bandOffsets[2] == 2) {
                return "RGB";
            } else if (bandOffsets[0] == 2 && bandOffsets[1] == 1 && bandOffsets[2] == 0) {
                return "BGR";
            }
        } else if (bandOffsets.length == 4) { // 检查带Alpha通道的情况
            if (bandOffsets[0] == 0 && bandOffsets[1] == 1 && bandOffsets[2] == 2 && bandOffsets[3] == 3) {
                return "RGBA";
            } else if (bandOffsets[0] == 2 && bandOffsets[1] == 1 && bandOffsets[2] == 0 && bandOffsets[3] == 3) {
                return "BGRA";
            }
        }

        return "Unknown or Other Type";
    }

    /**
     * 向给定的图片上绘制指定颜色的坐标框
     *
     * @param image  图片
     * @param frames 坐标框，可能有多个坐标框，每个坐标框内部为坐标点的集合
     * @param color  颜色
     */
    public static void drawImageWithFrames(BufferedImage image, ArrayList<ArrayList<Point>> frames, Color color) {

        if (frames == null || frames.isEmpty()) {
            return;
        }

        Graphics2D g2d = image.createGraphics();
        g2d.setColor(color);
        g2d.setStroke(new BasicStroke(2));
        // 遍历每个框
        for (ArrayList<Point> frame : frames) {
            // 检查每个框的点数
            if (frame.size() > 1) {
                // 使用点数组构建多边形
                int nPoints = frame.size();
                int[] xPoints = new int[nPoints];
                int[] yPoints = new int[nPoints];

                for (int i = 0; i < nPoints; i++) {
                    xPoints[i] = frame.get(i).getX().intValue();
                    yPoints[i] = frame.get(i).getY().intValue();
                }

                // 绘制多边形框
                g2d.drawPolygon(xPoints, yPoints, nPoints);
            }
        }
        g2d.dispose();
    }

    public static void drawImageWithBox(BufferedImage image, List<? extends Box> boxes) {
        Graphics2D g2d = image.createGraphics();
        // 1. 抗锯齿
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // 2. 动态字体与线宽
        int imgW = image.getWidth(), imgH = image.getHeight();
        int fontSize = Math.max(12, imgW / 100); // 最小 12
        Font font = new Font("Arial", Font.BOLD, fontSize);
        g2d.setFont(font);
        FontMetrics fm = g2d.getFontMetrics();
        float strokeWidth = Math.max(2f, fontSize / 4f);

        // 用于记录已经占用的标签区域，避免重叠
        List<Rectangle> occupied = new ArrayList<>();

        for (Box box : boxes) {
            if (box == null) {
                continue;
            }
            float x1 = box.getX1(), y1 = box.getY1();
            float x2 = box.getX2(), y2 = box.getY2();

            // 文本内容："类别 名称 + 置信度"
            String label = String.format("%s%s",
                    box.getTypeName() != null ? box.getTypeName() : "",
                    box.getConf() > 0 ? String.format(" %.2f", box.getConf()) : ""
            );
            int textW = fm.stringWidth(label);
            int textH = fm.getHeight();

            // 四个候选放置位置：上左、上右、下左、下右
            Point[] candidates = new Point[]{
                    new Point(x1, (y1 - textH)),
                    new Point((x2 - textW), (y1 - textH)),
                    new Point(x1, y2),
                    new Point((x2 - textW), y2)
            };

            Rectangle labelRect = null;
            for (Point p : candidates) {
                Rectangle r = new Rectangle(p.getX().intValue(), p.getY().intValue(), textW, textH);
                if (r.x < 0 || r.y < 0 || r.x + r.width > imgW || r.y + r.height > imgH) {
                    continue; // 越界
                }
                boolean conflict = false;
                for (Rectangle o : occupied) {
                    if (o.intersects(r)) {
                        conflict = true;
                        break;
                    }
                }
                if (!conflict) {
                    labelRect = r;
                    break;
                }
            }
            if (labelRect == null) {
                // 回退到框上方左侧，做边界裁剪
                int lx = Math.max(0, Math.min((int) x1, imgW - textW));
                int ly = Math.max(textH, Math.min((int) y1, imgH - textH));
                labelRect = new Rectangle(lx, ly - fm.getAscent(), textW, textH);
            }
            occupied.add(labelRect);

            // 3. 根据类别生成固定颜色
            Color boxColor;
            if (box.getTypeName() == null) {
                boxColor = BLUE;
            } else {
                boxColor = getColorFromString(box.getTypeName());
            }

            g2d.setStroke(new BasicStroke(strokeWidth));
            g2d.setColor(boxColor);
            // 4.a 绘制边框
            g2d.draw(new Rectangle2D.Float(x1, y1, x2 - x1, y2 - y1));
            // 4.b 半透明背景
            g2d.setColor(new Color(0, 0, 0, 128));
            g2d.fillRect(labelRect.x, labelRect.y, labelRect.width, labelRect.height);
            // 4.c 文字
            g2d.setColor(Color.WHITE);
            g2d.drawString(label, labelRect.x, labelRect.y + fm.getAscent());
        }

        g2d.dispose();
    }

    /**
     * 根据字符串生成一致的 HSB 颜色
     */
    private static Color getColorFromString(String key) {
        int hash = key.hashCode();
        float hue = (hash & 0xFFFFFF) / (float) 0xFFFFFF;      // [0,1]
        float saturation = 0.9f;                              // 高饱和度
        float brightness = 0.9f;                              // 高亮度
        return Color.getHSBColor(hue, saturation, brightness);
    }


    public static void drawImageWithKeypoints(BufferedImage image, java.util.List<BoxWithKeypoints> boxWithKeypoints) {
        drawImageWithBox(image, boxWithKeypoints);

        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        if (boxWithKeypoints.isEmpty()) {
            return;
        }

        for (BoxWithKeypoints boxWithKeypoint : boxWithKeypoints) {
            List<Point> keypoints = boxWithKeypoint.getKeypoints();
            if (keypoints == null || keypoints.isEmpty()) {
                return;
            }
            graphics.setColor(BLUE);
            for (Point p : keypoints) {
                graphics.fillOval(p.getX().intValue() - 4, p.getY().intValue() - 4, 8, 8);
            }

            graphics.setStroke(new BasicStroke(2));
            graphics.setColor(Color.GREEN);
            graphics.drawLine(keypoints.get(0).getX().intValue(), keypoints.get(0).getY().intValue(), keypoints.get(1).getX().intValue(), keypoints.get(1).getY().intValue());
            graphics.drawLine(keypoints.get(0).getX().intValue(), keypoints.get(0).getY().intValue(), keypoints.get(2).getX().intValue(), keypoints.get(2).getY().intValue());
            graphics.drawLine(keypoints.get(1).getX().intValue(), keypoints.get(1).getY().intValue(), keypoints.get(3).getX().intValue(), keypoints.get(3).getY().intValue());
            graphics.drawLine(keypoints.get(2).getX().intValue(), keypoints.get(2).getY().intValue(), keypoints.get(4).getX().intValue(), keypoints.get(4).getY().intValue());

            graphics.drawLine(keypoints.get(0).getX().intValue(), keypoints.get(0).getY().intValue(), keypoints.get(5).getX().intValue(), keypoints.get(5).getY().intValue());
            graphics.drawLine(keypoints.get(0).getX().intValue(), keypoints.get(0).getY().intValue(), keypoints.get(6).getX().intValue(), keypoints.get(6).getY().intValue());
            graphics.drawLine(keypoints.get(5).getX().intValue(), keypoints.get(5).getY().intValue(), keypoints.get(7).getX().intValue(), keypoints.get(7).getY().intValue());
            graphics.drawLine(keypoints.get(6).getX().intValue(), keypoints.get(6).getY().intValue(), keypoints.get(8).getX().intValue(), keypoints.get(8).getY().intValue());
            graphics.drawLine(keypoints.get(7).getX().intValue(), keypoints.get(7).getY().intValue(), keypoints.get(9).getX().intValue(), keypoints.get(9).getY().intValue());
            graphics.drawLine(keypoints.get(8).getX().intValue(), keypoints.get(8).getY().intValue(), keypoints.get(10).getX().intValue(), keypoints.get(10).getY().intValue());

            graphics.drawLine(keypoints.get(0).getX().intValue(), keypoints.get(0).getY().intValue(), keypoints.get(11).getX().intValue(), keypoints.get(11).getY().intValue());
            graphics.drawLine(keypoints.get(0).getX().intValue(), keypoints.get(0).getY().intValue(), keypoints.get(12).getX().intValue(), keypoints.get(12).getY().intValue());
            graphics.drawLine(keypoints.get(11).getX().intValue(), keypoints.get(11).getY().intValue(), keypoints.get(13).getX().intValue(), keypoints.get(13).getY().intValue());
            graphics.drawLine(keypoints.get(13).getX().intValue(), keypoints.get(13).getY().intValue(), keypoints.get(15).getX().intValue(), keypoints.get(15).getY().intValue());
            graphics.drawLine(keypoints.get(12).getX().intValue(), keypoints.get(12).getY().intValue(), keypoints.get(14).getX().intValue(), keypoints.get(14).getY().intValue());
            graphics.drawLine(keypoints.get(14).getX().intValue(), keypoints.get(14).getY().intValue(), keypoints.get(16).getX().intValue(), keypoints.get(16).getY().intValue());

        }
        graphics.dispose();
    }


    /**
     * 扩展并裁剪图像
     *
     * @param image 要裁剪的图像
     * @param box   裁剪区域的边界框
     * @param ratio 扩展比例
     * @return 裁剪后的图像
     * @throws IllegalArgumentException 如果参数无效
     */
    public static BufferedImage cropExpand(BufferedImage image, Box box, float ratio) {
        if (image == null || box == null) {
            throw new IllegalArgumentException("Image and box cannot be null");
        }
        if (ratio < 0) {
            throw new IllegalArgumentException("Ratio must be non-negative");
        }

        int imgW = image.getWidth();
        int imgH = image.getHeight();
        double width = box.getX2() - box.getX1();
        double height = box.getY2() - box.getY1();
        double padX = width * ratio;
        double padY = height * ratio;
        float nx1 = (float) Math.max(0, box.getX1() - padX);
        float ny1 = (float) Math.max(0, box.getY1() - padY);
        float nx2 = (float) Math.min(imgW, box.getX2() + padX);
        float ny2 = (float) Math.min(imgH, box.getY2() + padY);

        if (nx1 >= nx2 || ny1 >= ny2) {
            throw new IllegalArgumentException("Invalid crop dimensions");
        }

        Box paddedBox = new Box(nx1, ny1, nx2, ny2);
        return crop(image, paddedBox);
    }

    /**
     * 裁剪图像
     *
     * @param image 要裁剪的图像
     * @param box   裁剪区域的边界框
     * @return 裁剪后的图像
     */
    public static BufferedImage crop(BufferedImage image, Box box) {
        int x = Math.round(box.getX1());
        int y = Math.round(box.getY1());
        int w = Math.round(box.getX2() - box.getX1());
        int h = Math.round(box.getY2() - box.getY1());
        return image.getSubimage(x, y, Math.min(w, image.getWidth() - x), Math.min(h, image.getHeight() - y));
    }

    public static void drawImageWithListPoint(BufferedImage image, List<List<Point>> points) {
        Graphics2D g2d = image.createGraphics();
        g2d.setStroke(new BasicStroke(2)); // 线宽
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON); // 抗锯齿

        for (List<Point> contour : points) {
            if (contour.size() < 3) continue; // 至少3个点才能构成填充多边形

            // 构造路径
            GeneralPath path = new GeneralPath();
            Point first = contour.get(0);
            path.moveTo(first.getX(), first.getY());
            for (int i = 1; i < contour.size(); i++) {
                Point p = contour.get(i);
                path.lineTo(p.getX(), p.getY());
            }
            path.closePath(); // 闭合路径

            // 填充（半透明）
//            g2d.setColor(new Color(255, 0, 0, 80)); // 半透明红色
//            g2d.fill(path);

            // 描边
            g2d.setColor(Color.RED);
            g2d.draw(path);
        }

        g2d.dispose();
    }
}
