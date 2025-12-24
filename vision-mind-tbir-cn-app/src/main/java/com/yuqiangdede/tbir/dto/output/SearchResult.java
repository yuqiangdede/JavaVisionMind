package com.yuqiangdede.tbir.dto.output;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SearchResult {


    /**
     * 匹配的图像结果列表
     */
    private List<HitImage> results;

    /**
     * 总命中数
     */
    private int totalHits;

    public SearchResult(List<HitImage> finalList) {
        this.results = finalList;
        this.totalHits = finalList.size();
    }
}
