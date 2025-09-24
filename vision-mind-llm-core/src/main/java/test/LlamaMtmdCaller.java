package test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

public class LlamaMtmdCaller {
    public static void main(String[] args) throws Exception {
        List<String> cmd = Arrays.asList(
                "F:\\C++Code\\llama.cpp\\build\\bin\\Release\\llama-mtmd-cli.exe",
                "-m", "F:\\LM Models\\openbmb\\MiniCPM-V-4-gguf\\ggml-model-Q4_K_S.gguf",
                "--mmproj", "F:\\LM Models\\openbmb\\MiniCPM-V-4-gguf\\mmproj-model-f16.gguf",
                "--image", "F:\\Users\\yuqiang2\\Pictures\\1.png",
                "--prompt", "请用简体中文详细描述这张图片的内容，不要使用英文"
        );

        ProcessBuilder pb = new ProcessBuilder(cmd);
        // 日志重定向到文件，不干扰 stdout
//        pb.redirectError(new File("llama_logs.txt"));
        // stdout 保留管道
        pb.redirectErrorStream(false);
        Process proc = pb.start();

        // 1) 启一个线程专门读 stderr，直接丢弃或记录到文件
        new Thread(() -> {
            try (BufferedReader err = new BufferedReader(
                    new InputStreamReader(proc.getErrorStream(), StandardCharsets.UTF_8))) {
                while (err.readLine() != null) { /* 抛弃即可 */ }
            } catch (Exception ignored) {}
        }, "stderr-reader").start();


        // 主线程或第二线程：消费 stdout（白字部分）并存到变量
        StringBuilder whiteBuilder = new StringBuilder();
        try (BufferedReader outReader = new BufferedReader(
                new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = outReader.readLine()) != null) {
                whiteBuilder.append(line).append("\n");
            }
        }
        String whiteText = whiteBuilder.toString().trim();  // 这里就是所有“白字”内容



        // whiteText 已经包含完整的 stdout
        String[] parts = whiteText.split("\\R{2,}", 2);
        // \\R 匹配任何行终止，{2,} 表示连续两次及以上（即至少有一个空行）
        // limit 为 2，保证只分成两段

        String desc = "";
        if (parts.length == 2) {
            desc = parts[1].trim();
        }

        System.out.println(desc);
    }
}
