package cn.keking.service;

import cn.keking.config.ConfigConstants;
import cn.keking.model.FileAttribute;
import com.sun.star.document.UpdateDocMode;
import org.apache.commons.lang3.StringUtils;
import org.jodconverter.core.office.OfficeException;
import org.jodconverter.local.LocalConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

/**
 * @author yudian-it
 */
@Component
public class OfficeToPdfService {

    private final static Logger logger = LoggerFactory.getLogger(OfficeToPdfService.class);

    public void openOfficeToPDF(String inputFilePath, String outputFilePath, FileAttribute fileAttribute) throws OfficeException {
        office2pdf(inputFilePath, outputFilePath, fileAttribute);
    }
    public void openOfficeToPDF(String inputFilePath, String outputFilePath, FileAttribute fileAttribute, BiConsumer<FileAttribute, Boolean> runAfterConvert) throws OfficeException {
        office2pdf(inputFilePath, outputFilePath, fileAttribute, runAfterConvert);
    }


    public static void converterFile(File inputFile, String outputFilePath_end, FileAttribute fileAttribute) throws OfficeException {
        File outputFile = new File(outputFilePath_end);
        // 假如目标路径不存在,则新建该路径
        if (!outputFile.getParentFile().exists() && !outputFile.getParentFile().mkdirs()) {
            logger.error("创建目录【{}】失败，请检查目录权限！",outputFilePath_end);
        }
        LocalConverter.Builder builder;
        Map<String, Object> filterData = new HashMap<>();
        filterData.put("EncryptFile", true);
        if(!ConfigConstants.getOfficePageRange().equals("false")){
            filterData.put("PageRange", ConfigConstants.getOfficePageRange()); //限制页面
        }
        if(!ConfigConstants.getOfficeWatermark().equals("false")){
            filterData.put("Watermark", ConfigConstants.getOfficeWatermark());  //水印
        }
        filterData.put("Quality", ConfigConstants.getOfficeQuality()); //图片压缩
        filterData.put("MaxImageResolution", ConfigConstants.getOfficeMaxImageResolution()); //DPI
        if(ConfigConstants.getOfficeExportBookmarks()){
            filterData.put("ExportBookmarks", true); //导出书签
        }
        if(ConfigConstants.getOfficeExportNotes()){
            filterData.put("ExportNotes", true); //批注作为PDF的注释
        }
        if(ConfigConstants.getOfficeDocumentOpenPasswords()){
            filterData.put("DocumentOpenPassword", fileAttribute.getFilePassword()); //给PDF添加密码
        }
        Map<String, Object> customProperties = new HashMap<>();
        customProperties.put("FilterData", filterData);
        if (StringUtils.isNotBlank(fileAttribute.getFilePassword())) {
            Map<String, Object> loadProperties = new HashMap<>();
            loadProperties.put("Hidden", true);
            loadProperties.put("ReadOnly", true);
            loadProperties.put("UpdateDocMode", UpdateDocMode.NO_UPDATE);
            loadProperties.put("Password", fileAttribute.getFilePassword());
            builder = LocalConverter.builder().loadProperties(loadProperties).storeProperties(customProperties);
        } else {
            builder = LocalConverter.builder().storeProperties(customProperties);
        }
        builder.build().convert(inputFile).to(outputFile).execute();
    }
    public static void converterFileAsync(File inputFile, String outputFilePath_end, FileAttribute fileAttribute, BiConsumer<FileAttribute, Boolean> runAfterConvert) throws OfficeException {
        File outputFile = new File(outputFilePath_end);
        // 假如目标路径不存在,则新建该路径
        if (!outputFile.getParentFile().exists() && !outputFile.getParentFile().mkdirs()) {
            logger.error("创建目录【{}】失败，请检查目录权限！",outputFilePath_end);
        }
        LocalConverter.Builder builder;
        Map<String, Object> filterData = new HashMap<>();
        filterData.put("EncryptFile", true);
        if(!ConfigConstants.getOfficePageRange().equals("false")){
            filterData.put("PageRange", ConfigConstants.getOfficePageRange()); //限制页面
            // office 从区间取页有些麻烦，例如从 3-6,2-9,12,15,15-20 中取前三页的区间；精力有限，暂时将区间的处理方式设为同步
            fileAttribute.setAsync(false);
        }
        if(!ConfigConstants.getOfficeWatermark().equals("false")){
            filterData.put("Watermark", ConfigConstants.getOfficeWatermark());  //水印
        }
        filterData.put("Quality", ConfigConstants.getOfficeQuality()); //图片压缩
        filterData.put("MaxImageResolution", ConfigConstants.getOfficeMaxImageResolution()); //DPI
        if(ConfigConstants.getOfficeExportBookmarks()){
            filterData.put("ExportBookmarks", true); //导出书签
        }
        if(ConfigConstants.getOfficeExportNotes()){
            filterData.put("ExportNotes", true); //批注作为PDF的注释
        }
        if(ConfigConstants.getOfficeDocumentOpenPasswords()){
            filterData.put("DocumentOpenPassword", fileAttribute.getFilePassword()); //给PDF添加密码
        }
        Map<String, Object> customProperties = new HashMap<>();
        customProperties.put("FilterData", filterData);
        if (StringUtils.isNotBlank(fileAttribute.getFilePassword())) {
            Map<String, Object> loadProperties = new HashMap<>();
            loadProperties.put("Hidden", true);
            loadProperties.put("ReadOnly", true);
            loadProperties.put("UpdateDocMode", UpdateDocMode.NO_UPDATE);
            loadProperties.put("Password", fileAttribute.getFilePassword());
            builder = LocalConverter.builder().loadProperties(loadProperties).storeProperties(customProperties);
        } else {
            builder = LocalConverter.builder().storeProperties(customProperties);
        }

        if (fileAttribute.isAsync()) {
            // 同步转换前两页，以供快速预览
            filterData.put("PageRange", String.format("1-%d", fileAttribute.getAsyncPageNum()));
            String tmpOutputFilePath = outputFilePath_end.substring(0, outputFilePath_end.lastIndexOf("/") + 1)
                    + "tmp/"
                    + outputFilePath_end.substring(outputFilePath_end.lastIndexOf("/") + 1);
            builder.build().convert(inputFile).to(new File(tmpOutputFilePath)).execute();
            fileAttribute.setTmpOutFilePath(tmpOutputFilePath);
            // 异步转换整个文件
            CompletableFuture<Void> officeConvertFuture = CompletableFuture.runAsync(() -> {
                try {
                    filterData.remove("PageRange"); //不限制页面
                    builder.build().convert(inputFile).to(outputFile).execute();
                } catch (OfficeException e) {
                    logger.error(String.format("Office 转 PDF 失败：%s。", org.springframework.util.StringUtils.hasText(e.getMessage()) ? e.getMessage() : "发生错误"), e);
                }
                runAfterConvert.accept(fileAttribute, true);
            });
            fileAttribute.setOfficeConvertfuture(officeConvertFuture);
        } else {
            builder.build().convert(inputFile).to(outputFile).execute();
        }
        runAfterConvert.accept(fileAttribute, false);
    }


    public void office2pdf(String inputFilePath, String outputFilePath, FileAttribute fileAttribute) throws OfficeException {
        if (null != inputFilePath) {
            File inputFile = new File(inputFilePath);
            // 判断目标文件路径是否为空
            if (null == outputFilePath) {
                // 转换后的文件路径
                String outputFilePath_end = getOutputFilePath(inputFilePath);
                if (inputFile.exists()) {
                    // 找不到源文件, 则返回
                    converterFile(inputFile, outputFilePath_end, fileAttribute);
                }
            } else {
                if (inputFile.exists()) {
                    // 找不到源文件, 则返回
                    converterFile(inputFile, outputFilePath, fileAttribute);
                }
            }
        }
    }
    public void office2pdf(String inputFilePath, String outputFilePath, FileAttribute fileAttribute, BiConsumer<FileAttribute, Boolean> runAfterConvert) throws OfficeException {
        if (null != inputFilePath) {
            File inputFile = new File(inputFilePath);
            // 判断目标文件路径是否为空
            if (null == outputFilePath) {
                // 转换后的文件路径
                String outputFilePath_end = getOutputFilePath(inputFilePath);
                if (inputFile.exists()) {
                    // 找不到源文件, 则返回
                    converterFileAsync(inputFile, outputFilePath_end, fileAttribute, runAfterConvert);
                }
            } else {
                if (inputFile.exists()) {
                    // 找不到源文件, 则返回
                    converterFileAsync(inputFile, outputFilePath, fileAttribute, runAfterConvert);
                }
            }
        }
    }

    public static String getOutputFilePath(String inputFilePath) {
        return inputFilePath.replaceAll("."+ getPostfix(inputFilePath), ".pdf");
    }

    public static String getPostfix(String inputFilePath) {
        return inputFilePath.substring(inputFilePath.lastIndexOf(".") + 1);
    }

}
