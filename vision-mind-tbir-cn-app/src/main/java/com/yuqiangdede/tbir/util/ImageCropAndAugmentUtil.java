package com.yuqiangdede.tbir.util;

import com.yuqiangdede.common.dto.output.Box;
import com.yuqiangdede.tbir.dto.AugmentedImage;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.awt.image.RescaleOp;
import java.util.ArrayList;
import java.util.List;

import static com.yuqiangdede.tbir.config.Constant.AUGMENT_TYPES;

public class ImageCropAndAugmentUtil {


    /**
     * 对输入的图像进行裁剪和增强处理，生成增强后的图像列表。
     *
     * @param image 要进行处理的图像
     * @param boxes 包含待处理图像区域的矩形框列表
     * @return 包含增强后图像的列表
     */
    public static List<AugmentedImage> cropAndAugment(BufferedImage image, List<Box> boxes) {
        List<AugmentedImage> result = new ArrayList<>();
        int imgW = image.getWidth();
        int imgH = image.getHeight();

        for (Box box : boxes) {
            // box扩展10%的区域
            Box paddedBox = expandBox(box, imgW, imgH, 0.1f);
            // 裁剪
            BufferedImage cropped = crop(image, paddedBox);
            //在保持原图像长宽比例（aspect ratio）不变的前提下，将图像缩放到目标尺寸，并用灰色或黑色进行边缘填充（padding），使其变成一个正方形（通常是 224x224）
            BufferedImage resized = resizeWithAspectAndPad(cropped, 224);

            if (AUGMENT_TYPES.contains("original")) {
                result.add(new AugmentedImage(box, paddedBox, resized, "original"));
            }
            if (AUGMENT_TYPES.contains("flipH")) {
                result.add(new AugmentedImage(box, paddedBox, flipHorizontal(resized), "flipH"));
            }
            if (AUGMENT_TYPES.contains("rotate15")) {
                result.add(new AugmentedImage(box, paddedBox, rotate(resized, 15), "rotate15"));
            }
            if (AUGMENT_TYPES.contains("rotate-15")) {
                result.add(new AugmentedImage(box, paddedBox, rotate(resized, -15), "rotate-15"));
            }
            if (AUGMENT_TYPES.contains("blur")) {
                result.add(new AugmentedImage(box, paddedBox, blur(resized), "blur"));
            }
            if (AUGMENT_TYPES.contains("bright")) {
                result.add(new AugmentedImage(box, paddedBox, adjustBrightness(resized, 1.2f), "bright"));
            }
            if (AUGMENT_TYPES.contains("contrast")) {
                result.add(new AugmentedImage(box, paddedBox, adjustContrast(resized, 1.5f), "contrast"));
            }

        }

        return result;
    }

    /**
     * 扩展矩形框的尺寸
     *
     * @param box   原始矩形框对象
     * @param imgW  图像宽度
     * @param imgH  图像高度
     * @param ratio 扩展比例
     * @return 扩展后的矩形框对象
     */
    public static Box expandBox(Box box, int imgW, int imgH, float ratio) {
        float w = box.getX2() - box.getX1();
        float h = box.getY2() - box.getY1();
        float padX = w * ratio;
        float padY = h * ratio;
        float nx1 = Math.max(0, box.getX1() - padX);
        float ny1 = Math.max(0, box.getY1() - padY);
        float nx2 = Math.min(imgW, box.getX2() + padX);
        float ny2 = Math.min(imgH, box.getY2() + padY);
        return new Box(nx1, ny1, nx2, ny2);
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

    /**
     * 根据目标尺寸调整图片大小并保持其宽高比，同时使用填充来保持图片正方形。
     *
     * @param image      需要调整大小的图片
     * @param targetSize 目标尺寸
     * @return 调整大小并填充后的图片
     */
    public static BufferedImage resizeWithAspectAndPad(BufferedImage image, int targetSize) {
        int width = image.getWidth();
        int height = image.getHeight();
        int cropSize = Math.min(width, height);
        int x = (width - cropSize) / 2;
        int y = (height - cropSize) / 2;
        BufferedImage cropped = image.getSubimage(x, y, cropSize, cropSize);

        BufferedImage resized = new BufferedImage(targetSize, targetSize, BufferedImage.TYPE_3BYTE_BGR);
        Graphics2D g = resized.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(cropped, 0, 0, targetSize, targetSize, null);
        g.dispose();
        return resized;
    }

    /**
     * 水平翻转图像
     *
     * @param image 待翻转的图像
     * @return 水平翻转后的图像
     */
    public static BufferedImage flipHorizontal(BufferedImage image) {
        int w = image.getWidth(), h = image.getHeight();
        BufferedImage flipped = new BufferedImage(w, h, image.getType());
        Graphics2D g = flipped.createGraphics();
        g.drawImage(image, 0, 0, w, h, w, 0, 0, h, null);
        g.dispose();
        return flipped;
    }


    /**
     * 旋转图像。
     * @param image 需要旋转的原始图像。
     * @param angleDegrees 旋转的角度，以度为单位。
     * @return 返回旋转后的图像。
     */
    public static BufferedImage rotate(BufferedImage image, double angleDegrees) {
        double radians = Math.toRadians(angleDegrees);
        int w = image.getWidth();
        int h = image.getHeight();
        BufferedImage rotated = new BufferedImage(w, h, image.getType());
        Graphics2D g = rotated.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setTransform(AffineTransform.getRotateInstance(radians, w / 2.0, h / 2.0));
        g.drawImage(image, 0, 0, null);
        g.dispose();
        return rotated;
    }

    /**
     * 对传入的BufferedImage对象进行模糊处理。
     *
     * @param img 要进行模糊处理的BufferedImage对象
     * @return 模糊处理后的BufferedImage对象
     */
    public static BufferedImage blur(BufferedImage img) {
        float[] kernel = {
                1f / 9, 1f / 9, 1f / 9,
                1f / 9, 1f / 9, 1f / 9,
                1f / 9, 1f / 9, 1f / 9
        };
        return new ConvolveOp(new Kernel(3, 3, kernel)).filter(img, null);
    }

    public static BufferedImage adjustBrightness(BufferedImage img, float scale) {
        return new RescaleOp(scale, 0, null).filter(img, null);
    }

    /**
     * 调整图像的对比度
     *
     * @param img 要调整对比度的图像
     * @param contrast 对比度值，1.0表示原始对比度，大于1.0表示增加对比度，小于1.0表示降低对比度
     * @return 调整对比度后的图像
     */
    public static BufferedImage adjustContrast(BufferedImage img, float contrast) {
        float offset = 128 * (1 - contrast);
        BufferedImage contrasted = new BufferedImage(img.getWidth(), img.getHeight(), img.getType());
        Graphics2D g = contrasted.createGraphics();
        g.drawImage(img, new RescaleOp(contrast, offset, null), 0, 0);
        g.dispose();
        return contrasted;
    }
}
