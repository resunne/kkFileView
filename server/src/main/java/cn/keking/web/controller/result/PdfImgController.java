package cn.keking.web.controller.result;

import cn.keking.config.ConfigConstants;
import cn.keking.service.PdfHandlerService;
import cn.keking.service.bo.PdfImgCacheBo;
import cn.keking.utils.KkFileUtils;
import cn.keking.web.controller.result.req.PdfImgLoadReq;
import cn.keking.web.controller.result.res.PdfImgLoadRes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.Collections;
import java.util.Objects;

@RequestMapping("/pdfimg")
@RestController
public class PdfImgController {

    private final Logger logger = LoggerFactory.getLogger(PdfImgController.class);

    private final PdfHandlerService pdfHandlerService;
    @Value("${server.tomcat.uri-encoding:UTF-8}")
    private String uriEncoding;

    public PdfImgController(PdfHandlerService pdfHandlerService) {
        this.pdfHandlerService = pdfHandlerService;
    }

    @PostMapping("/loadImages")
    public Result<PdfImgLoadRes> loadImages(@RequestBody PdfImgLoadReq loadReq) {
        if (Objects.isNull(loadReq) || !StringUtils.hasText(loadReq.getFileName())) {
            return Result.createFail("请提供文件名！");
        }
        String fileName = loadReq.getFileName().trim();
        Integer offset = Objects.nonNull(loadReq.getOffset()) ? Math.max(0, loadReq.getOffset()) : 0;
        Integer limit = Objects.nonNull(loadReq.getLimit()) ? Math.max(0, loadReq.getLimit()) : 0;

        try {
            String fileNamePure = KkFileUtils.fileNameWithoutSuffix(URLDecoder.decode(fileName, uriEncoding));
            String filePath = ConfigConstants.getFileDir() + fileNamePure + ".pdf";
            PdfImgCacheBo pdfImgCacheBo = pdfHandlerService.loadPdf2jpgConvertedCache(offset, limit, filePath);
            PdfImgLoadRes pdfImgLoadRes = PdfImgLoadRes.builder().pageNum(Objects.nonNull(pdfImgCacheBo.getPageNum()) ? Math.max(0, pdfImgCacheBo.getPageNum()) : 0)
                    .idxPosition(Objects.nonNull(pdfImgCacheBo.getIdxPosition()) ? Math.max(0, pdfImgCacheBo.getIdxPosition()) : 0)
                    .imgUrls(CollectionUtils.isEmpty(pdfImgCacheBo.getImgUrls()) ? Collections.EMPTY_LIST : pdfImgCacheBo.getImgUrls())
                    .build();
            if (Objects.isNull(pdfImgCacheBo.getIdxPosition()) || 0 >= pdfImgCacheBo.getIdxPosition() || offset >= pdfImgCacheBo.getIdxPosition()) {
                return Result.createFail("文件正在处理中", pdfImgLoadRes);
            }
            return Result.createSuccess(pdfImgLoadRes);
        } catch (UnsupportedEncodingException e) {
            String errorMsg = String.format("文件[%s]缓存加载失败 %s", fileName, StringUtils.hasText(e.getMessage()) ? e.getMessage() : "发生错误");
            logger.error(errorMsg, e);
            return Result.createFail(errorMsg);
        }
    }

}
