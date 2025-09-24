package com.yuqiangdede.common.util;



import com.yuqiangdede.common.dto.Point;
import com.yuqiangdede.common.dto.output.Box;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;

import java.util.ArrayList;

public class GeometryUtils {

    // GeometryFactory 实例用于创建几何形状
    private static final GeometryFactory geometryFactory = new GeometryFactory();

    private static Polygon boxToPolygon(Box box) {
        // 将 Box 转换为 Polygon
        Coordinate[] coords = new Coordinate[]{
                new Coordinate(box.getX1(), box.getY1()),
                new Coordinate(box.getX2(), box.getY1()),
                new Coordinate(box.getX2(), box.getY2()),
                new Coordinate(box.getX1(), box.getY2()),
                new Coordinate(box.getX1(), box.getY1())
        };
        return geometryFactory.createPolygon(coords);
    }

    private static Geometry polygonFromPoints(ArrayList<Point> points) {
        // 添加最后一个点，保证 Polygon 闭合
        points.add(points.get(0));

        // 从 Point 列表创建一个 Polygon
        Coordinate[] coords = new Coordinate[points.size()];
        for (int i = 0; i < points.size(); i++) {
            coords[i] = new Coordinate(points.get(i).getX(), points.get(i).getY());
        }
        return geometryFactory.createPolygon(coords);
    }

    public static double calcOverlap(Box box, ArrayList<Point> points) {
        Polygon boxPolygon = boxToPolygon(box);
        Geometry detectionPolygon = polygonFromPoints(points);

        // 计算交集区域
        Geometry intersection = boxPolygon.intersection(detectionPolygon);

        // 计算重叠区域的面积和目标框的面积
        double intersectionArea = intersection.getArea();
        double boxArea = boxPolygon.getArea();

        // 计算百分比
        return (intersectionArea / boxArea);
    }
}
