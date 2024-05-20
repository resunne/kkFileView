package cn.keking.web.controller.result.req;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PdfImgLoadReq extends BaseReq {

    private String fileName;

    private Integer offset;

    private Integer limit;

}
