package cn.keking.web.controller.result.res;

import lombok.*;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PdfImgLoadRes extends BaseRes {

    private Integer pageNum;

    private Integer idxPosition;

    private List<String> imgUrls;

}
