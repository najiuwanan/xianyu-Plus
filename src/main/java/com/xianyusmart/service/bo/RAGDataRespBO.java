package com.xianyusmart.service.bo;

import lombok.Data;

/**
 * @date 2026/4/21 20:56
 * @description 向量数据库的数据包装对象
 */
@Data
public class RAGDataRespBO {

    private String documentId;

    private String goodsID;

    private String content;

    private String createTime;

}
