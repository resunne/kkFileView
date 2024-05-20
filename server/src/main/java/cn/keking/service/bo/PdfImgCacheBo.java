package cn.keking.service.bo;

import lombok.*;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PdfImgCacheBo extends BaseBo {

    private Integer pageNum;

    private Integer idxPosition;

    private List<String> imgUrls;

}
