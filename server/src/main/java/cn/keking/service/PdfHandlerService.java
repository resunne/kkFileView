package cn.keking.service;

import cn.keking.config.ConfigConstants;
import cn.keking.model.FileAttribute;
import cn.keking.model.FileType;
import cn.keking.service.bo.PdfImgCacheBo;
import cn.keking.service.cache.CacheService;
import cn.keking.service.cache.NotResourceCache;
import cn.keking.web.filter.BaseUrlFilter;
import com.itextpdf.text.pdf.PdfReader;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.tools.imageio.ImageIOUtil;
import org.apache.poi.EncryptedDocumentException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

@Component
@DependsOn(ConfigConstants.BEAN_NAME)
public class PdfHandlerService {

    private static final String PDF2JPG_IMAGE_FORMAT = ".jpg";
    private static final String PDF_PASSWORD_MSG = "password";
    private final Logger logger = LoggerFactory.getLogger(PdfHandlerService.class);
    private final String fileDir = ConfigConstants.getFileDir();
    private final CacheService cacheService;
    @Value("${server.tomcat.uri-encoding:UTF-8}")
    private String uriEncoding;

    public PdfHandlerService(CacheService cacheService) {
        this.cacheService = cacheService;
    }

    /**
     * pdf文件转换成jpg图片集
     * fileNameFilePath pdf文件路径
     * pdfFilePath pdf输出文件路径
     * pdfName     pdf文件名称
     * loadPdf2jpgCache 图片访问集合
     */
    public List<String> pdf2jpg(String fileNameFilePath, String pdfFilePath, String pdfName, FileAttribute fileAttribute) throws Exception {
        boolean forceUpdatedCache = fileAttribute.forceUpdatedCache();
        boolean usePasswordCache = fileAttribute.getUsePasswordCache();
        String filePassword = fileAttribute.getFilePassword();
        String pdfPassword = null;
        PDDocument doc = null;
        PdfReader pdfReader = null;
        if (!forceUpdatedCache) {
            List<String> cacheResult = this.loadPdf2jpgCache(pdfFilePath);
            if (!CollectionUtils.isEmpty(cacheResult)) {
                return cacheResult;
            }
        }
        List<String> imageUrls = new ArrayList<>();
        try {
            File pdfFile = new File(fileNameFilePath);
            if (!pdfFile.exists()) {
                return null;
            }
            doc = PDDocument.load(pdfFile, filePassword);
            doc.setResourceCache(new NotResourceCache());
            int pageCount = doc.getNumberOfPages();
            PDFRenderer pdfRenderer = new PDFRenderer(doc);
            int index = pdfFilePath.lastIndexOf(".");
            String folder = pdfFilePath.substring(0, index);
            File path = new File(folder);
            if (!path.exists() && !path.mkdirs()) {
                logger.error("创建转换文件【{}】目录失败，请检查目录权限！", folder);
            }
            String imageFilePath;
            for (int pageIndex = 0; pageIndex < pageCount; pageIndex++) {
                imageFilePath = folder + File.separator + pageIndex + PDF2JPG_IMAGE_FORMAT;
                BufferedImage image = pdfRenderer.renderImageWithDPI(pageIndex, ConfigConstants.getPdf2JpgDpi(), ImageType.RGB);
                ImageIOUtil.writeImage(image, imageFilePath, ConfigConstants.getPdf2JpgDpi());
                String imageUrl = this.getPdf2jpgUrl(pdfFilePath, pageIndex);
                imageUrls.add(imageUrl);
            }
            try {
                if (!ObjectUtils.isEmpty(filePassword)) {  // 获取到密码 判断是否是加密文件
                    pdfReader = new PdfReader(fileNameFilePath);   // 读取PDF文件 通过异常获取该文件是否有密码字符
                }
            } catch (Exception e) {  // 获取异常方法 判断是否有加密字符串
                Throwable[] throwableArray = ExceptionUtils.getThrowables(e);
                for (Throwable throwable : throwableArray) {
                    if (throwable instanceof IOException || throwable instanceof EncryptedDocumentException) {
                        if (e.getMessage().toLowerCase().contains(PDF_PASSWORD_MSG)) {
                            pdfPassword = PDF_PASSWORD_MSG;  // 查询到该文件是密码文件 输出带密码的值
                        }
                    }
                }
                if (!PDF_PASSWORD_MSG.equals(pdfPassword)) {  // 该文件异常 错误原因非密码原因输出错误
                    logger.error("Convert pdf exception, pdfFilePath：{}", pdfFilePath, e);
                }
            } finally {
                if (pdfReader != null) {   // 关闭
                    pdfReader.close();
                }
            }

            if (usePasswordCache || !PDF_PASSWORD_MSG.equals(pdfPassword)) {   // 加密文件  判断是否启用缓存命令
                this.addPdf2jpgCache(pdfFilePath, pageCount);
            }
        } catch (IOException e) {
            if (!e.getMessage().contains(PDF_PASSWORD_MSG)) {
                logger.error("Convert pdf to jpg exception, pdfFilePath：{}", pdfFilePath, e);
            }
            throw new Exception(e);
        } finally {
            if (doc != null) {   // 关闭
                doc.close();
            }
        }
        return imageUrls;
    }


    /**
     * pdf文件转换成jpg图片集
     * fileNameFilePath pdf文件路径
     * pdfFilePath pdf输出文件路径
     * pdfName     pdf文件名称
     * loadPdf2jpgCache 图片访问集合
     */
    public List<String> pdf2jpgAsync(String fileNameFilePath, String pdfFilePath, String pdfName, FileAttribute fileAttribute) throws Exception {
        boolean forceUpdatedCache = fileAttribute.forceUpdatedCache();
        boolean usePasswordCache = fileAttribute.getUsePasswordCache();
        String filePassword = fileAttribute.getFilePassword();
        PDDocument doc = null;
        CompletableFuture<Void> future = null;
        boolean asyncActivateFlag = false;
        boolean readTmpFileFlag = false;

        // 尝试从缓存获取预览数据
        if (!forceUpdatedCache) {
            List<String> cacheResult = this.loadPdf2jpgCache(pdfFilePath);
            if (!CollectionUtils.isEmpty(cacheResult)) {
                return cacheResult;
            }
        }

        List<String> imageUrls = new ArrayList<>();
        try {
            // 加载文件
            /**
             * 读取临时文件或完整文件
             * - 若文件是 PDF，则加载完整文件
             * - 若文件是 Office
             *  - 异步转换为 PDF 未完成，则加载临时文件
             *  - 异步转换为 PDF 已完成，则加载完整文件
             */
            String readPdfFile;
            if (!fileAttribute.isAsync() || FileType.PDF.equals(fileAttribute.getType()) || fileAttribute.getOfficeConvertfuture().isDone()) {
                readPdfFile = pdfFilePath;
            } else {
                readPdfFile = fileAttribute.getTmpOutFilePath();
                readTmpFileFlag = true;
            }
            File pdfFile = new File(readPdfFile);
            if (!pdfFile.exists()) {
                return null;
            }

            // 文件输出位置
            int index = pdfFilePath.lastIndexOf(".");
            String folder = pdfFilePath.substring(0, index);
            File path = new File(folder);
            if (!path.exists() && !path.mkdirs()) {
                logger.error("创建转换文件【{}】目录失败，请检查目录权限！", folder);
            }

            // 校验 PDF 文件密码
            boolean passwordRequiredFlag = checkPasswordRequired(fileNameFilePath, pdfFilePath, filePassword);

            // 读取文件
            doc = PDDocument.load(pdfFile, filePassword);

            // 内容读取
            doc.setResourceCache(new NotResourceCache());
            PDFRenderer pdfRenderer = new PDFRenderer(doc);

            // 初始页面读取页数
            int pageCount = doc.getNumberOfPages();
            int asyncStartIndex;
            if (readTmpFileFlag) {
                asyncStartIndex = pageCount;
                asyncActivateFlag = true;
            } else if (!fileAttribute.isAsync() || pageCount <= fileAttribute.getAsyncPageNum()) {
                asyncStartIndex = pageCount;
            } else {
                asyncStartIndex = ConfigConstants.getOfficePreviewImgInitNum();
                asyncActivateFlag = true;
            }

            // 初始页面读取
            pdf2jpgLoop(0, asyncStartIndex, imageUrls, folder, pdfFilePath, pdfRenderer);

            // 缓存文件信息
            if (usePasswordCache || !passwordRequiredFlag) {   // 加密文件  判断是否启用缓存命令
                if (!readTmpFileFlag) {
                    this.addPdf2jpgCache(pdfFilePath, pageCount);
                }
                this.addPdf2jpgCurrentIndexCache(pdfFilePath, pageCount);
            }

            // 异步读取其余页面
            if (asyncActivateFlag) {
                PdfFileReadFunction pdfFileReadFunction = (asyncDoc, asyncPdfRenderer, asyncPageCount) -> {
                    try {
                        if (usePasswordCache || !passwordRequiredFlag) {
                            pdf2jpgLoop(asyncStartIndex, asyncPageCount, imageUrls, folder, pdfFilePath, asyncPdfRenderer, true);
                        } else {
                            pdf2jpgLoop(asyncStartIndex, asyncPageCount, imageUrls, folder, pdfFilePath, asyncPdfRenderer);
                        }
                    } catch (Exception e) {
                        logger.error(String.format("创建转换图片文件失败：%s", StringUtils.hasText(e.getMessage()) ? e.getMessage() : "发生错误"), e);
                    } finally {
                        if (Objects.nonNull(asyncDoc)) {
                            try {
                                asyncDoc.close();
                            } catch (IOException e) {
                                logger.error(String.format("PDF 文件关闭失败：%s", StringUtils.hasText(e.getMessage()) ? e.getMessage() : "发生错误"), e);
                            }
                        }
                    }
                };

                if (readTmpFileFlag) {
                    if (Objects.nonNull(doc)) {
                        doc.close();
                    }
                    future = fileAttribute.getOfficeConvertfuture().thenRun(() -> {
                        try {
                            final PDDocument asyncDoc = PDDocument.load(new File(pdfFilePath), filePassword);
                            asyncDoc.setResourceCache(new NotResourceCache());
                            final int asyncPageCount = asyncDoc.getNumberOfPages();
                            final PDFRenderer asyncPdfRenderer = new PDFRenderer(asyncDoc);
                            if (usePasswordCache || !passwordRequiredFlag) {   // 加密文件  判断是否启用缓存命令
                                this.addPdf2jpgCache(pdfFilePath, asyncPageCount);
                            }
                            pdfFileReadFunction.read(asyncDoc, asyncPdfRenderer, asyncPageCount);
                        } catch (Exception e) {
                            logger.error(String.format("读取文件%s失败：%s", pdfFile, StringUtils.hasText(e.getMessage()) ? e.getMessage() : "发生错误"), e);
                        }
                    });
                } else {
                    PDDocument finalDoc = doc;
                    future = CompletableFuture.runAsync(() -> pdfFileReadFunction.read(finalDoc, pdfRenderer, pageCount));
                }
            }
        } catch (IOException e) {
            if (!e.getMessage().contains(PDF_PASSWORD_MSG)) {
                logger.error("Convert pdf to jpg exception, pdfFilePath：{}", pdfFilePath, e);
            }
            throw new Exception(e);
        } finally {
            if (asyncActivateFlag && future.isDone() && null != doc) {   // 关闭
                doc.close();
            }
        }
        return imageUrls;
    }

    /**
     * 获取缓存中的 pdf 转换成 jpg 图片集
     *
     * @param pdfFilePath pdf文件路径
     * @return 图片访问集合
     */
    public List<String> loadPdf2jpgCache(String pdfFilePath) {
        List<String> imageUrls = new ArrayList<>();
        Integer imageCount = this.getPdf2jpgCache(pdfFilePath);
        if (Objects.isNull(imageCount)) {
            return imageUrls;
        }
        IntStream.range(0, imageCount).forEach(i -> {
            String imageUrl = this.getPdf2jpgUrl(pdfFilePath, i);
            imageUrls.add(imageUrl);
        });
        return imageUrls;
    }


    /**
     * 获取本地 pdf 转 image 后的 web 访问地址
     *
     * @param pdfFilePath pdf文件名
     * @param index       图片索引
     * @return 图片访问地址
     */
    private String getPdf2jpgUrl(String pdfFilePath, int index) {
        String baseUrl = BaseUrlFilter.getBaseUrl();
        pdfFilePath = pdfFilePath.replace(fileDir, "");
        String pdfFolder = pdfFilePath.substring(0, pdfFilePath.length() - 4);
        String urlPrefix;
        try {
            urlPrefix = baseUrl + URLEncoder.encode(pdfFolder, uriEncoding).replaceAll("\\+", "%20");
        } catch (UnsupportedEncodingException e) {
            logger.error("UnsupportedEncodingException", e);
            urlPrefix = baseUrl + pdfFolder;
        }
        return urlPrefix + "/" + index + PDF2JPG_IMAGE_FORMAT;
    }

    /**
     * @param key pdf本地路径
     * @return 已将pdf转换成图片的图片本地相对路径
     */
    public Integer getPdf2jpgCache(String key) {
        return cacheService.getPdfImageCache(key);
    }

    public Integer getPdf2jpgConvertIndexCache(String key) {
        return cacheService.getPdfConvertIndexCache(key);
    }

    /**
     * 添加转换后图片组缓存
     *
     * @param pdfFilePath pdf文件绝对路径
     * @param num         图片张数
     */
    public void addPdf2jpgCache(String pdfFilePath, int num) {
        cacheService.putPdfImageCache(pdfFilePath, num);
    }

    public void addPdf2jpgCurrentIndexCache(String pdfFilePath, int num) {
        cacheService.putPdfConvertIndexCache(pdfFilePath, num);
    }

    /**
     * 验证 PDF 文件需不需要密码
     *
     * @param fileNameFilePath pdf文件路径
     * @param pdfFilePath      pdf输出文件路径
     * @param filePassword     pdf文件密码
     * @return 是否存在密码
     */
    public boolean checkPasswordRequired(String fileNameFilePath, String pdfFilePath, String filePassword) {
        PdfReader pdfReader = null;
        boolean pdfWithPassword = false;
        try {
            if (!ObjectUtils.isEmpty(filePassword)) {  // 获取到密码 判断是否是加密文件
                pdfReader = new PdfReader(fileNameFilePath);   // 读取PDF文件 通过异常获取该文件是否有密码字符
            }
        } catch (Exception e) {  // 获取异常方法 判断是否有加密字符串
            Throwable[] throwableArray = ExceptionUtils.getThrowables(e);
            for (Throwable throwable : throwableArray) {
                if (throwable instanceof IOException || throwable instanceof EncryptedDocumentException) {
                    if (e.getMessage().toLowerCase().contains(PDF_PASSWORD_MSG)) {
                        pdfWithPassword = true;  // 查询到该文件是密码文件 输出带密码的值
                    }
                }
            }
            if (!pdfWithPassword) {  // 该文件异常 错误原因非密码原因输出错误
                logger.error("Convert pdf exception, pdfFilePath：{}", pdfFilePath, e);
            }
        } finally {
            if (pdfReader != null) {   // 关闭
                pdfReader.close();
            }
        }
        return pdfWithPassword;
    }

    public String getImageFilePath(String folder, int pageIndex) {
        return folder + File.separator + pageIndex + PDF2JPG_IMAGE_FORMAT;
    }

    public void pdf2jpgLoop(int startIndex, int endIndex, List<String> imageUrls
            , String folder, String pdfFilePath, PDFRenderer pdfRenderer, boolean isCurrentCache) throws Exception {
        String imageFilePath;
        for (int pageIndex = startIndex; pageIndex < endIndex; pageIndex++) {
            imageFilePath = getImageFilePath(folder, pageIndex);
            BufferedImage image = pdfRenderer.renderImageWithDPI(pageIndex, ConfigConstants.getPdf2JpgDpi(), ImageType.RGB);
            ImageIOUtil.writeImage(image, imageFilePath, ConfigConstants.getPdf2JpgDpi());
            String imageUrl = this.getPdf2jpgUrl(pdfFilePath, pageIndex);
            imageUrls.add(imageUrl);
            if (isCurrentCache) {
                this.addPdf2jpgCurrentIndexCache(pdfFilePath, pageIndex);
            }
        }
    }

    public void pdf2jpgLoop(int startIndex, int endIndex, List<String> imageUrls
            , String folder, String pdfFilePath, PDFRenderer pdfRenderer) throws Exception {
        pdf2jpgLoop(startIndex, endIndex, imageUrls, folder, pdfFilePath, pdfRenderer, false);
    }


    /**
     * 获取缓存中的 pdf 转换成 jpg 图片集（仅已转换的成功的，由于存在异步，文件可能仍未全部转换完成）
     *
     * @param pdfFilePath pdf文件路径
     * @return 图片访问集合
     */
    public PdfImgCacheBo loadPdf2jpgConvertedCache(int offset, int limit, String pdfFilePath) {
        offset = Math.max(offset, 0);
        int startIndex = offset + 1;

        List<String> imageUrls = new ArrayList<>();
        PdfImgCacheBo.PdfImgCacheBoBuilder boBuilder = PdfImgCacheBo.builder().imgUrls(imageUrls);
        // 获取缓存页数（不存在，则未开始转换）
        Integer imageCount = this.getPdf2jpgCache(pdfFilePath);
        boBuilder.pageNum(imageCount);
        if (Objects.isNull(imageCount) || startIndex > imageCount) {
            return boBuilder.idxPosition(imageCount).build();
        }
        // 获取当前已转换的图片（不存在，则未开始转换）
        Integer imageConvertedIndex = this.getPdf2jpgConvertIndexCache(pdfFilePath);
        if (Objects.isNull(imageConvertedIndex) || startIndex > imageConvertedIndex + 1) {
            return boBuilder.idxPosition(imageConvertedIndex).build();
        }
        int endIndex = 0 >= limit || imageConvertedIndex + 1 < startIndex + limit - 1 ? imageConvertedIndex + 1 : startIndex + limit - 1;
        boBuilder.idxPosition(endIndex);
        // 获取图片链接
        IntStream.range(startIndex, endIndex + 1).forEach(i -> imageUrls.add(this.getPdf2jpgUrl(pdfFilePath, i - 1)));
        return boBuilder.build();
    }
}
