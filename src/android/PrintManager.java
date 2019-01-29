/*
    Copyright 2013-2016 appPlant GmbH

    Licensed to the Apache Software Foundation (ASF) under one
    or more contributor license agreements.  See the NOTICE file
    distributed with this work for additional information
    regarding copyright ownership.  The ASF licenses this file
    to you under the Apache License, Version 2.0 (the
    "License"); you may not use this file except in compliance
    with the License.  You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing,
    software distributed under the License is distributed on an
    "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
    KIND, either express or implied.  See the License for the
    specific language governing permissions and limitations
    under the License.
 */

package de.appplant.cordova.plugin.printer;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.print.PrintHelper;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.apache.cordova.engine.SystemWebView;
import org.apache.cordova.engine.SystemWebViewClient;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;

import static android.content.Context.PRINT_SERVICE;
import static android.os.Build.VERSION.SDK_INT;
import static de.appplant.cordova.plugin.printer.PrintContent.ContentType.UNSUPPORTED;

class PrintManager {

    // The application context
    private final @NonNull Context context;

    private @Nullable WebView webView;

    /**
     * Constructor
     *
     * @param context The context where to look for.
     */
    PrintManager (@NonNull Context context)
    {
        this.context = context;
    }

    /**
     * If the print framework is able to render the referenced file.
     *
     * @param item Any kind of URL like file://, file:///, res:// or base64://
     *
     * @return true if its able to render the content of the file.
     */
    boolean canPrintItem (@Nullable String item)
    {
        boolean supported = PrintHelper.systemSupportsPrint();

        if (item != null)
        {
            supported = PrintContent.getContentType(item, context) != UNSUPPORTED;
        }

        return supported;
    }

    /**
     * List of all printable document types (utis).
     */
    @NonNull
    static JSONArray getPrintableUTIs()
    {
        JSONArray utis = new JSONArray();

        utis.put("com.adobe.pdf");
        utis.put("com.microsoft.bmp");
        utis.put("public.jpeg");
        utis.put("public.jpeg-2000");
        utis.put("public.png");
        utis.put("public.heif");
        utis.put("com.compuserve.gif");
        utis.put("com.microsoft.ico");

        return utis;
    }

    /**
     * Sends the provided content to the printing controller and opens
     * them.
     *
     * @param content  The content or file to print.
     * @param settings Additional settings how to render the content.
     * @param callback The function to invoke once the job is done.
     */
    void print (@Nullable String content, @NonNull JSONObject settings,
                @NonNull View view,
                @Nullable PrintHelper.OnPrintFinishCallback callback)
    {
        switch (PrintContent.getContentType(content, context))
        {
            case IMAGE:
                //noinspection ConstantConditions
                printImage(content, settings, callback);
                break;
            case PDF:
                //noinspection ConstantConditions
                printPdf(content, settings, callback);
                break;
            case HTML:
                if (content == null || content.isEmpty()) {
                    printView(view, settings);
                } else {
                    printHtml(content, settings);
                }
            case PLAIN:
                // TODO implement me
            default:
                // TODO unsupported content
        }
    }

    /**
     * Prints the content of the specified view.
     *
     * @param view     The web view instance to print.
     * @param settings Additional settings how to render the content.
     */
    private void printView (@NonNull View view, @NonNull JSONObject settings)
    {
        PrintOptions options  = new PrintOptions(settings);
        String jobName        = options.getJobName();
        WebView webView       = (WebView) view;

        ((Activity) context).runOnUiThread(() -> {
            PrintDocumentAdapter adapter;

            if (SDK_INT >= 21) {
                adapter = webView.createPrintDocumentAdapter(jobName);
            } else {
                adapter = webView.createPrintDocumentAdapter();
            }

            printAdapter(adapter, options);
        });
    }

    /**
     * Prints the HTML markup.
     *
     * @param content  The HTML markup to print.
     * @param settings Additional settings how to render the content.
     */
    private void printHtml (@NonNull String content,
                            @NonNull JSONObject settings)
    {
        ((Activity) context).runOnUiThread(() -> {
            webView = new WebView(context);

            webView.setWebViewClient(new WebViewClient() {
                @Override
                public boolean shouldOverrideUrlLoading (WebView view, String url) {
                    return false;
                }

                @Override
                public void onPageFinished (WebView view, String url) {
                    printView(webView, settings);
                    webView = null;
                }
            });

            webView.loadDataWithBaseURL("file:///android_asset/www/", content, "text/html", "UTF-8",
                    null);
        });
    }

    /**
     * Prints the provided PDF document.
     *
     * @param path     The path to the file to print.
     * @param settings Additional settings how to render the content.
     * @param callback The function to invoke once the job is done.
     */
    private void printPdf (@NonNull String path, @NonNull JSONObject settings,
                           @Nullable PrintHelper.OnPrintFinishCallback callback)
    {
        InputStream stream    = PrintContent.open(path, context);

        if (stream == null) return;

        PrintOptions options  = new PrintOptions(settings);
        String jobName        = options.getJobName();
        PrintAdapter adapter  = new PrintAdapter(jobName, stream, callback);

        printAdapter(adapter, options);
    }

    /**
     * Prints the content provided by the print adapter.
     *
     * @param adapter The adapter that holds the content.
     * @param options Additional settings how to render the content.
     */
    private void printAdapter (@NonNull PrintDocumentAdapter adapter,
                               @NonNull PrintOptions options)
    {
        String jobName        = options.getJobName();
        PrintAttributes attrs = options.toPrintAttributes();

        getPrintService().print(jobName, adapter, attrs);
    }

    /**
     * Prints the specified image by file uri.
     *
     * @param path     The path to the file to print.
     * @param settings Additional settings how to render the content.
     * @param callback The function to invoke once the job is done.
     */
    private void printImage (@NonNull String path, @NonNull JSONObject settings,
                             @Nullable PrintHelper.OnPrintFinishCallback callback)
    {
        Bitmap bitmap        = PrintContent.decode(path, context);

        if (bitmap == null) return;

        PrintOptions options = new PrintOptions(settings);
        PrintHelper printer  = new PrintHelper(context);
        String jobName       = options.getJobName();

        options.decoratePrintHelper(printer);

        printer.printBitmap(jobName, bitmap, callback);
    }

    /**
     * Returns the print service of the app.
     */
    @NonNull
    private android.print.PrintManager getPrintService()
    {
        return (android.print.PrintManager) context.getSystemService(PRINT_SERVICE);
    }
}