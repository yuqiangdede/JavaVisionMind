package com.yuqiangdede.platform.common.image;

import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.util.Base64;

public class ImageLoader {

    public BufferedImage load(ImageSource source, MultipartFile file) throws IOException {
        if (source == null) {
            throw new IllegalArgumentException("image source is empty");
        }
        ImageSource.Type type = source.getType() == null ? ImageSource.Type.URL : source.getType();
        return switch (type) {
            case URL -> loadFromUrl(source.getUrl());
            case BASE64 -> loadFromBase64(source.getBase64());
            case UPLOAD -> loadFromUpload(file);
        };
    }

    public BufferedImage loadFromUrl(String url) throws IOException {
        if (!StringUtils.hasText(url)) {
            throw new IllegalArgumentException("url is empty");
        }
        try {
            URL resolved = URI.create(url).toURL();
            BufferedImage image = ImageIO.read(resolved);
            if (image == null) {
                throw new IOException("unsupported image");
            }
            return image;
        } catch (RuntimeException ex) {
            throw new IOException("invalid url", ex);
        }
    }

    public BufferedImage loadFromBase64(String base64) throws IOException {
        if (!StringUtils.hasText(base64)) {
            throw new IllegalArgumentException("base64 is empty");
        }
        String payload = base64;
        int comma = base64.indexOf(',');
        if (base64.startsWith("data:") && comma > 0) {
            payload = base64.substring(comma + 1);
        }
        byte[] bytes = Base64.getDecoder().decode(payload);
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
        if (image == null) {
            throw new IOException("base64 image decode failed");
        }
        return image;
    }

    public BufferedImage loadFromUpload(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("upload file is empty");
        }
        BufferedImage image = ImageIO.read(file.getInputStream());
        if (image == null) {
            throw new IOException("unsupported upload image");
        }
        return image;
    }
}
