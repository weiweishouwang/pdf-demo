package com.zhw.pdf;

import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.*;
import com.itextpdf.kernel.pdf.annot.PdfAnnotation;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.kernel.pdf.canvas.parser.PdfCanvasProcessor;
import com.itextpdf.kernel.pdf.canvas.parser.PdfDocumentContentParser;
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor;
import com.itextpdf.kernel.pdf.canvas.parser.listener.IPdfTextLocation;
import com.itextpdf.kernel.pdf.canvas.parser.listener.RegexBasedLocationExtractionStrategy;
import com.itextpdf.kernel.pdf.xobject.PdfImageXObject;
import com.itextpdf.layout.element.Image;


import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

/**
 * Hello world!
 *
 */
public class App 
{
    private static final String src = "/Users/hongweizou/Downloads/pdf/src/";
    private static final String desc = "/Users/hongweizou/Downloads/pdf/desc/";

    public static void main( String[] args ) throws Exception {
        System.out.println("Hello World!");

        String desc = "/Users/hongweizou/Downloads/test.pdf";
        //String src = "/Users/hongweizou/Downloads/GUIDE_354a90d2d8.pdf";
        //String src = "/Users/hongweizou/Downloads/GUIDE_58556eb183.pdf";
        //String src = "/Users/hongweizou/Downloads/【医脉通】女性抗栓治疗的中国专家建议.pdf";
        String src = "/Users/hongweizou/Downloads/【医脉通】糖尿病患者血糖波动管理专家共识.pdf";


        manipulatePdf(src, desc);
        //removeAll();
    }

    public static void removeAll() throws IOException {
        Files.list(Paths.get(src)).forEach(file -> {
            String descFile = desc + file.getFileName().toString();
            System.out.println(descFile);
            try {
                manipulatePdf(file.toString(), descFile);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public static void removeLink(PdfDictionary page) {
        removeAnnots(page);
    }

    public static void removeText(PdfDocument pdfDoc) {
        for (int i = 1; i <= pdfDoc.getNumberOfPages(); i++) {
            PdfPage page = pdfDoc.getPage(i);

            PdfArray pdfObject = (PdfArray) page.getPdfObject().get(PdfName.Contents);
            Iterator<PdfObject> it = pdfObject.iterator();
            while (it.hasNext()) {
                PdfObject object = it.next();
                System.out.println(object.getIndirectReference());
                object.isStream();
                if (object.isStream()) {

                }
            }

            RegexBasedLocationExtractionStrategy strategy = new RegexBasedLocationExtractionStrategy("http://guide.medlive.cn/");
            PdfCanvasProcessor canvasProcessor = new PdfCanvasProcessor(strategy);
            canvasProcessor.processPageContent(page);
            Collection<IPdfTextLocation> resultantLocations = strategy.getResultantLocations();

            PdfCanvas canvas = new PdfCanvas(page.newContentStreamAfter(),
                    page.getResources(), pdfDoc);
            canvas.saveState();

            for (IPdfTextLocation location : resultantLocations) {
                canvas.setFillColor(ColorConstants.WHITE);
                canvas.rectangle(location.getRectangle().getX() - 1, location.getRectangle().getY() - 1
                        , location.getRectangle().getWidth() + 1, location.getRectangle().getHeight() + 1);
                canvas.fill();
                canvas.restoreState();
                canvas.beginText();
                canvas.endText();
            }
        }
    }

    public static void removeAnnots(PdfDictionary page) {
        PdfArray annots = page.getAsArray(PdfName.Annots);
        if (annots == null) {
            return;
        }

        Iterator<PdfObject> iterator = annots.iterator();
        while (iterator.hasNext()) {
            PdfObject pdfObject = iterator.next();
            if (pdfObject.isDictionary()) {
                PdfDictionary next = (PdfDictionary) pdfObject;
                PdfDictionary uriDic = next.getAsDictionary(PdfName.A);
                if (uriDic != null) {
                    String uri = uriDic.getAsString(PdfName.URI).getValue();
                    System.out.println(uri);
                    if (uri == null || uri.contains("medlive") || uri.trim().equals("")) {
                        System.out.println("delete link " + uri);
                        iterator.remove();
                    }
                }
            }
        }
    }

    /**
     * 获取高度小于60、宽度小于200，且数量大于页数一半的图片名字
     * @param pdfDoc
     * @return
     */
    public static List<String> getImageName(PdfDocument pdfDoc) {
        Map<String, Integer> imageMap = new HashMap<>();
        List<String> list = new ArrayList<>();
        int pageCount = pdfDoc.getNumberOfPages();

        for (int i = 1; i <= pdfDoc.getNumberOfPages(); i++) {
            PdfDictionary page = pdfDoc.getPage(i).getPdfObject();
            PdfDictionary resources = page.getAsDictionary(PdfName.Resources);
            PdfDictionary xobjects = resources.getAsDictionary(PdfName.XObject);
            Iterator<Map.Entry<PdfName, PdfObject>> it = xobjects.entrySet().iterator();

            while (it.hasNext()) {
                Map.Entry<PdfName, PdfObject> next = it.next();
                if (next.getValue() != null && next.getValue().isStream()) {
                    PdfStream stream = (PdfStream) next.getValue();
                    String type = stream.getAsName(PdfName.Subtype).getValue();
                    if ("Image".equals(type)) {
                        double width = stream.getAsNumber(PdfName.Width).getValue();
                        double height = stream.getAsNumber(PdfName.Height).getValue();
                        System.out.println("name " + next.getKey().getValue());
                        System.out.println("width " + width);
                        System.out.println("hight " + height);
                        //if (width < 200 && height < 60) {
                            imageMap.compute(next.getKey().getValue(), (key, value) -> value == null ? 1 : ++value);
                        //}
                    }
                }
            }
        }

        System.out.println(imageMap);
        imageMap.forEach((k, v) -> {
            if (2 * v > pageCount) {
                list.add(k);
            }
        });
        System.out.println(list);

        return list;
    }

    /**
     * 删除每页最后一张图片
     * @param page
     */
    public static void removeImage(List<String> imageList, PdfDictionary page) {
        PdfDictionary resources = page.getAsDictionary(PdfName.Resources);
        PdfDictionary xobjects = resources.getAsDictionary(PdfName.XObject);
        Iterator<Map.Entry<PdfName, PdfObject>> it = xobjects.entrySet().iterator();
        //PdfName pdfName = null;
        while (it.hasNext()) {
            //pdfName = null;
            Map.Entry<PdfName, PdfObject> next = it.next();
            if (imageList.contains(next.getKey().getValue())) {
                //pdfName = next.getKey();
                System.out.println("delete image " + next.getKey().getValue());
                it.remove();
            }
        }

        // 删除最后一个符合条件的
        /*if (pdfName != null) {
            System.out.println("delete image " + pdfName.getValue());
            xobjects.remove(pdfName);
        }*/
    }

    public static void manipulatePdf(String src, String dest) throws Exception {
        PdfDocument pdfDoc = new PdfDocument(new PdfReader(src), new PdfWriter(dest));
        List<String> imageList = getImageName(pdfDoc);
        for (int i = 1; i <= pdfDoc.getNumberOfPages(); i++) {
            PdfDictionary page = pdfDoc.getPage(i).getPdfObject();
            //page.remove(PdfName.Contents);
            removeImage(imageList, page);
            removeLink(page);
        }
        removeText(pdfDoc);

        pdfDoc.close();
    }

    public static void getKeyWordsLocation(String input, String key, int pageNum){
        RegexBasedLocationExtractionStrategy strategy = new RegexBasedLocationExtractionStrategy(key);

        try{
            //核心思路为对PdfDocument对象采用某种Strategy，这里使用RegexBasedLocationExtractionStrategy
            PdfReader pr = new PdfReader(input);
            PdfDocument pd = new PdfDocument(pr);
            PdfDocumentContentParser pdcp = new PdfDocumentContentParser(pd);

            //文本内容具体解析借助使用PdfDocumentContentParser类(实质使用PdfCanvasProcessor进行处理)， 对待处理页面装配合适策略
            RegexBasedLocationExtractionStrategy regexStrategy =
                    pdcp.processContent(pageNum, strategy);
            //获取处理结果
            Collection<IPdfTextLocation> resultantLocations = strategy.getResultantLocations();
            //自定义结果处理
            if (!resultantLocations.isEmpty()){
                for(IPdfTextLocation item: resultantLocations){
                    Rectangle boundRectangle = item.getRectangle();
                    System.out.println(item.getText());
                    System.out.println("["+key + "] location of x: " + boundRectangle.getX() + "  ,y: " + boundRectangle.getY());
                }
            }
            else {
                System.out.println("the result is null");
            }
            pr.close();
            pd.close();

        }catch (Exception e){
            System.err.println("read file failed!");
            e.printStackTrace();
        }
    }

    /**
     * 获取关键字的坐标
     * @param keyMap
     * @return
     */
    public static  List<List<Object>> getTextPosition(String sourcePath,String finishPath, Map<String,String> keyMap,Map<String,String> replaceMap) throws IOException {

        PdfReader reader = new PdfReader(sourcePath);
        PdfDocument pdfDocument = new PdfDocument(reader, new PdfWriter(finishPath));

        for (int i = 1; i <= pdfDocument.getNumberOfPages(); i++) {

            for (String key  : keyMap.keySet()) {

                PdfPage page = pdfDocument.getPage(i);

                RegexBasedLocationExtractionStrategy strategy = new RegexBasedLocationExtractionStrategy(keyMap.get(key));
                PdfCanvasProcessor canvasProcessor = new PdfCanvasProcessor(strategy);
                canvasProcessor.processPageContent(page);
                Collection<IPdfTextLocation> resultantLocations = strategy.getResultantLocations();
                PdfCanvas pdfCanvas = new PdfCanvas(page);
                pdfCanvas.setLineWidth(0.5f);

                for (IPdfTextLocation location : resultantLocations) {
                    Rectangle rectangle = location.getRectangle();
                    pdfCanvas.rectangle(rectangle);
                    pdfCanvas.setStrokeColor(ColorConstants.RED);
                    pdfCanvas.stroke();

                }
            }

        }
        pdfDocument.close();

        return null;
    }

    /**
     * 覆盖原有的内容 并填充新内容
     *
     * @param sourcePath 源文件
     * @param finishPath 替换后的文件
     * @param list
     */
    public static void overText(String sourcePath, String finishPath, List<List<Object>> list, PdfFont font) throws IOException {
        PdfDocument pdfDoc = new PdfDocument(new PdfReader(sourcePath), new PdfWriter(finishPath));
        //pdfDoc.getFirstPage().newContentStreamAfter() 会覆盖掉字体
        //pdfDoc.getFirstPage().newContentStreamBefore() 只会在字体的下层添加一个背景色
        for (int i = 1; i <= pdfDoc.getNumberOfPages(); i++) {
            PdfCanvas canvas = new PdfCanvas(pdfDoc.getPage(i).newContentStreamAfter(),
                    pdfDoc.getPage(i).getResources(), pdfDoc);

            canvas.saveState();
            //List<OverAreaDTO> overAreaDTOS = list.get(i-1);
            //用白色背景覆盖原本的字体
            //for (OverAreaDTO overArea :overAreaDTOS) {
                canvas.setFillColor(ColorConstants.WHITE);
                //覆盖的时候y + 0.35   填充字体的时候 + 1.5 主要就是避免覆盖占位符下面的线
             //   canvas.rectangle(overArea.getX(), overArea.getY() + 0.35, overArea.getWidth(), overArea.getHeight());
            //}
            canvas.fill();
            canvas.restoreState();

            //填充新内容
            canvas.beginText();
            /*for (OverAreaDTO overArea :overAreaDTOS) {
                canvas.setFontAndSize(font,overArea.getHeight());
                canvas.setTextMatrix(overArea.getX(),overArea.getY() + 1.5f);
                canvas.newlineShowText(overArea.getValue());
            }*/
            canvas.endText();
        }

        pdfDoc.close();

    }

    public static void replaceStream(PdfStream orig, PdfStream stream) throws IOException {
        orig.clear();
        orig.setData(stream.getBytes());
        for (PdfName name : stream.keySet()) {
            orig.put(name, stream.get(name));
        }
    }

    public static Image makeBlackAndWhitePng(PdfImageXObject image) throws IOException {
        BufferedImage bi = image.getBufferedImage();
        BufferedImage newBi = new BufferedImage(bi.getWidth(), bi.getHeight(), BufferedImage.TYPE_USHORT_GRAY);
        newBi.getGraphics().drawImage(bi, 0, 0, null);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(newBi, "png", baos);
        return new Image(ImageDataFactory.create(baos.toByteArray()));
    }

}
