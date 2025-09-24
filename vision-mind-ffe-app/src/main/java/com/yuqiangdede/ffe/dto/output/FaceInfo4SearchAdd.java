package com.yuqiangdede.ffe.dto.output;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Setter
@Getter
public class FaceInfo4SearchAdd {
    List<FaceInfo4Add> addList;
    List<FaceInfo4Search> searchList;

    public FaceInfo4SearchAdd(List<FaceInfo4Add> addList, List<FaceInfo4Search> searchList) {
        this.addList = addList;
        this.searchList = searchList;
    }

    @Override
    public String toString() {
        return "FaceInfo4SearchAdd{" +
                "addList=" + addList +
                ", searchList=" + searchList +
                '}';
    }
}
