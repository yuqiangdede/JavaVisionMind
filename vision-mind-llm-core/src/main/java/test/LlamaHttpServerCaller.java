package test;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class LlamaHttpServerCaller {
    public static void main(String[] args) throws Exception {
        // 1. 本地图片路径
        String imagePath = "C:\\Users\\yuqiang2\\Pictures\\1212.jpg";

        // 2. 读取并转成 Base64 Data URI
        String dataUri = imageFileToDataURI(imagePath);

        // 3. 构造 JSON 请求体
        String json = """
{
  "model": "llama",
  "messages": [
    {
      "role": "user",
      "content": [
        { "type": "text",
          "text": "请描述这张图片" },
        { "type": "image_url",
          "image_url": {
            "url": "http://10.19.169.37/ngx/proxy?i=aHR0cHM6Ly8xMC4xOS4xNjkuMzc6NjExMy9waWM/KmQ4Zj0wMGRjMzAybC1hZmRvMDEtMio5YmRlOTNkMTdmMTI1YmIqNWYyPT1zcCoqMTEzdD0qNzcxMj04MzU0OTgzKjc0Mjk9M2w1KjY1NTg9Mm8tYjE3cGIwLTFvPTFjMmkwMDllMTA9MGMwJkFjY2Vzc0tleUlkPUJUZ1VjMnRSYW1samNDOWsmRXhwaXJlcz0xNzU4Mjg2NzMwJlNpZ25hdHVyZT13UC9ZVXRueVhNNk02aXRWT0Zab2RiTmlmWkE9JkFsZ29yaXRobT0w"
          } }
      ]
    }
  ],
  "stream": false
}
        """.formatted(dataUri);

        // 4. 发送 HTTP POST 到本地 llama-server
        URL url = new URL("http://10.19.169.37:8080/v1/chat/completions");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(json.getBytes(StandardCharsets.UTF_8));
        }

        // 5. 输出响应
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }
        }
    }

    // 工具方法：本地图片转 Data URI
    public static String imageFileToDataURI(String path) throws IOException {
        // 1. 读取原图
        BufferedImage original = ImageIO.read(new File(path));

        // 2. 创建缩放图像 100x100
        BufferedImage resized = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = resized.createGraphics();
        g.drawImage(original, 0, 0, 100, 100, null);
        g.dispose();

        // 3. 写入内存流
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        String ext = path.toLowerCase().endsWith(".png") ? "png" : "jpg";
        ImageIO.write(resized, ext, baos);

        // 4. 转 base64
        String base64 = Base64.getEncoder().encodeToString(baos.toByteArray());
        return "data:image/" + (ext.equals("jpg") ? "jpeg" : ext) + ";base64," + base64;
    }
}
