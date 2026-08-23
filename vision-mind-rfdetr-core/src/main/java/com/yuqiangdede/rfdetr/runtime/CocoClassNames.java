package com.yuqiangdede.rfdetr.runtime;

import java.util.LinkedHashMap;
import java.util.Map;

public final class CocoClassNames {

    private CocoClassNames() {
    }

    public static Map<Integer, String> standard() {
        Map<Integer, String> names = new LinkedHashMap<>();
        names.put(1, "person"); names.put(2, "bicycle"); names.put(3, "car"); names.put(4, "motorcycle");
        names.put(5, "airplane"); names.put(6, "bus"); names.put(7, "train"); names.put(8, "truck");
        names.put(9, "boat"); names.put(10, "traffic light"); names.put(11, "fire hydrant");
        names.put(13, "stop sign"); names.put(14, "parking meter"); names.put(15, "bench"); names.put(16, "bird");
        names.put(17, "cat"); names.put(18, "dog"); names.put(19, "horse"); names.put(20, "sheep");
        names.put(21, "cow"); names.put(22, "elephant"); names.put(23, "bear"); names.put(24, "zebra");
        names.put(25, "giraffe"); names.put(27, "backpack"); names.put(28, "umbrella"); names.put(31, "handbag");
        names.put(32, "tie"); names.put(33, "suitcase"); names.put(34, "frisbee"); names.put(35, "skis");
        names.put(36, "snowboard"); names.put(37, "sports ball"); names.put(38, "kite"); names.put(39, "baseball bat");
        names.put(40, "baseball glove"); names.put(41, "skateboard"); names.put(42, "surfboard"); names.put(43, "tennis racket");
        names.put(44, "bottle"); names.put(46, "wine glass"); names.put(47, "cup"); names.put(48, "fork");
        names.put(49, "knife"); names.put(50, "spoon"); names.put(51, "bowl"); names.put(52, "banana");
        names.put(53, "apple"); names.put(54, "sandwich"); names.put(55, "orange"); names.put(56, "broccoli");
        names.put(57, "carrot"); names.put(58, "hot dog"); names.put(59, "pizza"); names.put(60, "donut");
        names.put(61, "cake"); names.put(62, "chair"); names.put(63, "couch"); names.put(64, "potted plant");
        names.put(65, "bed"); names.put(67, "dining table"); names.put(70, "toilet"); names.put(72, "tv");
        names.put(73, "laptop"); names.put(74, "mouse"); names.put(75, "remote"); names.put(76, "keyboard");
        names.put(77, "cell phone"); names.put(78, "microwave"); names.put(79, "oven"); names.put(80, "toaster");
        names.put(81, "sink"); names.put(82, "refrigerator"); names.put(84, "book"); names.put(85, "clock");
        names.put(86, "vase"); names.put(87, "scissors"); names.put(88, "teddy bear"); names.put(89, "hair drier");
        names.put(90, "toothbrush");
        return Map.copyOf(names);
    }
}
