<!DOCTYPE html>
<html lang="en">
<head>

    <#if !fileCacheName??>
        <#assign fileCacheName>
            <#if imgUrls?? && 0 < imgUrls?size && null != imgUrls?first && 0 < imgUrls?first?length && 2 <= imgUrls?first?split("/")?size>
                ${imgUrls?first?split("/")[imgUrls?first?split("/")?size - 2]}
            <#elseif currentUrl?? && 0 < currentUrl?length && 2 <= currentUrl?split("/")?size>
                ${currentUrl?split("/")[currentUrl?split("/")?size - 2]}
            </#if>
        </#assign>
    </#if>

    <meta charset="utf-8"/>
    <title>${fileCacheName?trim}预览</title>
    <#include "*/commonHeader.ftl">
    <script src="js/lazyload.js"></script>
    <script src="js/jquery-3.6.1.min.js"></script>
    <style>
        body {
            background-color: #404040;
        }

        .container {
            width: 100%;
            height: 100%;
        }

        .img-area {
            text-align: center;
        }

        .my-photo {
            max-width: 98%;
            border-radius: 3px;
            box-shadow: rgba(0, 0, 0, 0.15) 0 0 8px;
            background: #FBFBFB;
            border: 1px solid #ddd;
            margin: 1px auto;
            padding: 5px;
        }

        #loading_lazy_tip {
            position: fixed;
            bottom: 0;
            width: 100%;
            height: 50px;
            text-align: center;
            margin-bottom: 15px;
        }

        #loading_lazy_tip div {
            height: 100%;
            line-height: 50px;
            display: inline-block;
            border: none;
            vertical-align: middle;
            color: red;
        }

        #loading_lazy_tip img {
            height: 100%;
        }

    </style>
</head>
<body>
<label>
    <input id="file_cache_name_input" type="text" style="display: none;visibility: hidden;" readonly disabled
           value="${fileCacheName?trim}">
</label>
<div class="container" id="container">
    <#list imgUrls as img>
        <div class="img-area">
            <img class="my-photo" alt="loading" data-src="${img}" src="images/loading.gif">
        </div>
    </#list>
</div>
<div id="loading_lazy_tip">
    <div>
        <img alt="加载中" src="images/loading_soon.gif">
    </div>
    <div>
        正在加载
        <span id="page_info_span">
             <span>：共<span id="view_page_num">-</span>页，</span>
             <span>还余</span><span id="left_page_num">-</span><span>页</span>
         </span>
        <span>……</span>
    </div>
</div>
<#if "false" == switchDisabled>
    <img src="images/pdf.svg" width="48" height="48"
         style="position: fixed; cursor: pointer; top: 40%; right: 48px; z-index: 999;" alt="使用PDF预览"
         title="使用PDF预览" onclick="changePreviewType('pdf')"/>
</#if>
<script>

    $(() => {
        loadingTipHide();
        updateLoadedNum();
    })

    window.onload = function () {
        /*初始化水印*/
        initWaterMark();
        checkImgs();

        loadFileCacheName();
    };

    function changePreviewType(previewType) {
        var url = window.location.href;
        if (url.indexOf("officePreviewType=image") !== -1) {
            url = url.replace("officePreviewType=image", "officePreviewType=" + previewType);
        } else {
            url = url + "&officePreviewType=" + previewType;
        }
        if ('allImages' === previewType) {
            window.open(url)
        } else {
            window.location.href = url;
        }
    }

    // 初始加载页面数量
    const INIT_NUM = <#if initNum??>${initNum}<#else>2</#if>;
    // 加载完整文件失败时，最大可重试次数[
    const RELOAD_ALL_DATA_COUNT_MAX = 3;
    // 加载完整文件失败时，已重试次数
    let reloadAllDataCount = 0;
    // 完整文件是否加载完成
    let loadingDataFlag = false;
    // 页总数
    let pageNum = <#if pageNum??>${pageNum}<#else> - 1</#if>;
    // 已加载页数
    <#-- let loadedNum = <#if loadedNum??>${loadedNum}<#elseif imgUrls??>${imgUrls?size}<#else> - 1</#if>; -->
    let loadedNum;

    function loadFileCacheName() {
        let $fileCacheNameInput = $('#file_cache_name_input');
        let fileCacheName = $fileCacheNameInput.val();
        if (!fileCacheName || !$.trim(fileCacheName)) {
            $fileCacheNameInput.val(getFileCacheNameFromViewImgs());
        }
    }

    function getFileCacheNameFromViewImgs() {
        let $firstFileViewImg = $('img.my-photo:first');
        if (undefined === $firstFileViewImg) {
            return '';
        }
        let imgUrl = $firstFileViewImg.attr('data-src');
        if (!imgUrl || !$.trim(imgUrl)) {
            return '';
        }
        let imgUrlPathItemArr = imgUrl.split('/');
        if (2 > imgUrlPathItemArr.length) {
            return '';
        }
        return imgUrlPathItemArr[imgUrlPathItemArr.length - 2];
    }

    function render_all_data(imgUrls) {
        if (Array.isArray(imgUrls) && imgUrls.length) {
            $.each(imgUrls, (idx, url) => {
                let img_element = $('<img/>', {
                    'class': 'my-photo',
                    'alt': 'loading',
                    'src': 'images/loading.gif',
                    'data-src': url
                });

                let div_img_element = $('<div/>', {
                    'class': 'img-area',
                    html: img_element
                });

                div_img_element.appendTo('#container');
            })
        }
    }

    function loadAllData() {
        if (!loadingDataFlag && (loadedNum < 0 || pageNum < 0 || loadedNum < pageNum)) {
            loadingDataFlag = true;
            loadingTipShow();
            // 文件名
            let fileCacheName = $('#file_cache_name_input').val();

            // 加载数据
            $.ajax({
                method: "POST"
                , contentType: 'application/json'
                , url: 'pdfimg/loadImages'
                , data: JSON.stringify({fileName: encodeURIComponent(fileCacheName), offset: loadedNum})
            })
                .done((res, status, xhr) => {
                    if (res.code && '200' === String(res.code)) {
                        let imgUrls = res.data.imgUrls;
                        if (!Array.isArray(imgUrls)) {
                            alert('获取的文件数据有误，已获取的数据不是有效的数组！');
                        } else if (0 >= imgUrls.length) {
                            alert('文件数据未更新，5秒后将重新加载！');
                            setTimeout(loadAllData, 5000)
                        } else {
                            render_all_data(imgUrls);
                            pageNum = res.data.pageNum;
                            updateLoadedNum();
                        }
                    }
                })
                .fail(() => {
                    reloadAllDataCount++;
                    if (reloadAllDataCount <= RELOAD_ALL_DATA_COUNT_MAX) {
                        alert('完整文件加载失败，5秒后将重新加载！');
                        setTimeout(loadAllData, 5000)
                    } else {
                        alert('完整文件预览无法完成，请重试！');
                    }
                })
                .always(() => {
                    if (loadedNum < pageNum) {
                        loadingDataFlag = false;
                    }
                    showImg();
                    loadingTipHide();
                });
        }
    }

    function isSrcollArriveLastImg() {
        let docHeight = $(document).height();
        let winScrollTop = $(window).scrollTop();
        let winHeight = $(window).height();
        if (docHeight - winScrollTop <= winHeight * 1.5) {
            if (pageNum > loadedNum) {
                return true;
            }
        }
        return false;
    }

    function isLastImgInSight() {
        let $lastViewImg = $('img.my-photo:last');
        return isInSight($lastViewImg[0]);
    }

    function loadingTipShow() {
        if (loadedNum > 0 && pageNum > loadedNum) {
            $('#view_page_num').text(pageNum);
            $('#left_page_num').text(pageNum - loadedNum);
            $('#page_info_span').show();
        } else {
            $('#page_info_span').hide();
        }
        $('#loading_lazy_tip').show();
    }

    function loadingTipHide() {
        // $('#view_page_num').text(pageNum);
        // $('#left_page_num').text(loadedNum);
        $('#loading_lazy_tip').hide();
    }

    function updateLoadedNum() {
        loadedNum = $('img.my-photo').length;
    }

    function showImg() {
        let $imgHidden = $('img.my-photo[src="images/loading.gif"]');
        if (undefined !== $imgHidden && 0 < $imgHidden.length) {
            let length = $imgHidden.length;
            for (let idx = 0; idx < length; idx++) {
                if (isInWindowSight($imgHidden[idx])) {
                    for (let startIdx = Math.max(0, idx - 2); startIdx < Math.min(length, idx + 2); startIdx++) {
                        loadImg($imgHidden[startIdx])
                    }
                    return;
                }
            }
        }
    }

    let winScrollTop = 0;

    function scrollTimeOut() {
        let curWinScrollTop = $(window).scrollTop();
        let windowHeight = $(window).height();
        if (0 === winScrollTop || curWinScrollTop - winScrollTop > windowHeight * 1.5) {
            setTimeout(scrollTimeOut, 5000);
        } else {
            showImg();
        }
        winScrollTop = curWinScrollTop;
    }

    function isInWindowSight(el) {
        let winScrollTop = $(window).scrollTop();
        let winHeight = $(window).height();
        let $el = el;
        if (!(el instanceof jQuery)) {
            $el = $(el);
        }
        let elTop = $el.offset().top;
        let elBottom = $el.offset().top + $el.height();
        if (elTop - winScrollTop < winHeight * 1.6 && elTop - winScrollTop >= 0 || elBottom - winScrollTop < winHeight * 1.6 && elBottom - winScrollTop >= 0) {
            return true;
        }
        return false;
    }

    function renderImgs() {
        if (isLastImgInSight()) {
            loadAllData();
        } else {
            showImg();
        }
    }

    window.onscroll = renderImgs;
    // window.onscrollend = renderImgs;
</script>
</body>
</html>
