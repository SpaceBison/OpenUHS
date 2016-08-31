package net.vhati.openuhs.desktopreader.downloader;

import java.awt.Component;
import java.awt.EventQueue;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InterruptedIOException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.UnknownHostException;
import java.net.URLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;
import javax.swing.JOptionPane;
import javax.swing.ProgressMonitor;
import javax.swing.ProgressMonitorInputStream;

import net.vhati.openuhs.core.UHSErrorHandler;
import net.vhati.openuhs.core.UHSErrorHandlerManager;
import net.vhati.openuhs.core.downloader.CatalogParser;
import net.vhati.openuhs.core.downloader.DownloadableUHS;


/**
 * A collection of utility methods for downloading/saving files and parsing the official UHS catalog.
 */
public class UHSFetcher {
  public static String catalogUrl = CatalogParser.DEFAULT_CATALOG_URL;
  public static String userAgent = CatalogParser.DEFAULT_USER_AGENT;

  private static CatalogParser catalogParser = new CatalogParser();


  /**
   * Downloads a url and spawns a progress monitor.
   *
   * @param parentComponent a component to be the monitor's parent
   * @param message message describing the operation
   * @param address source url of the data
   * @return the downloaded data
   */
  public static byte[] fetchURL(final Component parentComponent, Object message, String address) {
    final UHSErrorHandler errorHandler = UHSErrorHandlerManager.getErrorHandler();
    HttpURLConnection.setFollowRedirects(false);

    Exception exceptionObj = null;
    String exceptionMsg = null;
    try {
      URL url = new URL(address);
      URLConnection connection = url.openConnection();
      if (connection instanceof HttpURLConnection) {
        HttpURLConnection httpConnection = (HttpURLConnection)connection;
          httpConnection.setRequestProperty("User-Agent", userAgent);
          httpConnection.connect();
        int response = httpConnection.getResponseCode();
        if (response < 200 || response >= 300) {
          if (errorHandler != null) errorHandler.log(UHSErrorHandler.ERROR, null, "Could not fetch file. Response code: "+ response, 0, null);
          return null;
        }
        ProgressMonitorInputStream is = new ProgressMonitorInputStream(parentComponent, message, httpConnection.getInputStream());
        ProgressMonitor pm = is.getProgressMonitor();
          pm.setMaximum(httpConnection.getContentLength());
          pm.setMillisToDecideToPopup(300);
          pm.setMillisToPopup(100);
        int chunkSize = 1024;
        List<ByteBuffer> bufs = new ArrayList<ByteBuffer>();
        int tmpByte;
        ByteBuffer buf = ByteBuffer.allocate(chunkSize);
        bufs.add(buf);
        while ((tmpByte = is.read()) != -1) {
          buf.put((byte)tmpByte);
          if (buf.remaining()==0) {
            buf = ByteBuffer.allocate(chunkSize);
            bufs.add(buf);
          }
        }
        is.close();

        int bufCount = bufs.size();
        int bufTotalSize = chunkSize * bufCount;
        if (bufCount > 0)
          bufTotalSize -= bufs.get(bufCount-1).remaining();
        byte[] bytes = new byte[bufTotalSize];
        for (int i=0; i <= bufCount-2; i++) {
          buf = bufs.get(i);
          buf.rewind();
          buf.get(bytes, i*chunkSize, chunkSize);
        }
        if (bufCount > 0) {
          buf = bufs.get(bufCount-1);
          buf.flip();
          buf.get(bytes, (bufCount-1)*chunkSize, buf.remaining());
        }
        return bytes;
      }
    }
    catch (InterruptedIOException e) {/* user cancelled */}
    catch (UnknownHostException e) {
      exceptionObj = e;
      exceptionMsg = "Could not locate remote server";
    }
    catch(ConnectException e) {
      exceptionObj = e;
      exceptionMsg = "Could not connect to remote server";
    }
    catch (IOException e) {
      exceptionObj = e;
      exceptionMsg = "Could not download from server";
    }
    if (exceptionObj != null) {
      final Exception finalObj = exceptionObj;
      final String finalMsg = exceptionMsg;
      Runnable r = new Runnable() {
        @Override
        public void run() {
          if (errorHandler != null) errorHandler.log(UHSErrorHandler.ERROR, null, finalMsg, 0, finalObj);
          JOptionPane.showMessageDialog(parentComponent, finalMsg +":\n "+ finalObj.getMessage(), "OpenUHS Cannot Continue", JOptionPane.ERROR_MESSAGE);
        }
      };
      EventQueue.invokeLater(r);
    }

    return null;
  }


  /**
   *
   * Downloads a catalog of available hint files.
   *
   * @param parentComponent a component to be the monitor's parent
   * @return an array of DownloadableUHS objects
   */
  public static List<DownloadableUHS> fetchCatalog(Component parentComponent) {
    List<DownloadableUHS> catalog = new ArrayList<DownloadableUHS>();

    byte[] responseBytes = fetchURL(parentComponent, "Fetching Catalog...", catalogUrl);
    if (responseBytes == null) return catalog;

    String catalogString = new String(responseBytes);
    catalog = catalogParser.parseCatalog(catalogString);

    return catalog;
  }


  /**
   *
   * Downloads a hint file.
   *
   * @param parentComponent a component to be the monitor's parent
   * @param uhs hint to fetch
   * @return the downloaded data
   */
  public static byte[] fetchUHS(Component parentComponent, DownloadableUHS uhs) {
    UHSErrorHandler errorHandler = UHSErrorHandlerManager.getErrorHandler();
    byte[] fullBytes = null;

    if (errorHandler != null) errorHandler.log(UHSErrorHandler.INFO, null, "Fetching "+ uhs.getName() +"...", 0, null);
    byte[] responseBytes = fetchURL(parentComponent, "Fetching "+ uhs.getName() +"...", uhs.getUrl());
    if (responseBytes == null) return fullBytes;

    ZipInputStream is = new ZipInputStream(new ByteArrayInputStream(responseBytes));
    try {
      //No need for a while loop; only one file
      //  and contrary to the doc, zip errors can occur if there is no next entry
      ZipEntry ze;
      if ((ze = is.getNextEntry()) != null) {
        if (errorHandler != null) errorHandler.log(UHSErrorHandler.INFO, null, "Extracting "+ ze.getName() +"...", 0, null);

        int chunkSize = 1024;
        List<ByteBuffer> bufs = new ArrayList<ByteBuffer>();
        int tmpByte;
        ByteBuffer buf = ByteBuffer.allocate(chunkSize);
        bufs.add(buf);
        while ((tmpByte = is.read()) != -1) {
          buf.put((byte)tmpByte);
          if (buf.remaining()==0) {
            buf = ByteBuffer.allocate(chunkSize);
            bufs.add(buf);
          }
        }

        int bufCount = bufs.size();
        int bufTotalSize = chunkSize * bufCount;
        if (bufCount > 1)
          bufTotalSize -= bufs.get(bufCount-1).remaining();
        fullBytes = new byte[bufTotalSize];
        for (int i=0; i <= bufCount-2; i++) {
          buf = bufs.get(i);
          buf.rewind();
          buf.get(fullBytes, i*chunkSize, chunkSize);
        }
        if (bufCount > 1) {
          buf = bufs.get(bufCount-1);
          buf.flip();
          buf.get(fullBytes, (bufCount-1)*chunkSize, buf.remaining());
        }

      }
      is.close();
    }
    catch (ZipException e) {
      if (errorHandler != null) errorHandler.log(UHSErrorHandler.ERROR, null, "Could not extract downloaded hints for "+ uhs.getName(), 0, e);
    }
    catch (IOException e) {
      if (errorHandler != null) errorHandler.log(UHSErrorHandler.ERROR, null, "Could not extract downloaded hints for "+ uhs.getName(), 0, e);
    }
    finally {
      try{is.close();}
      catch (Exception f) {}
    }

    return fullBytes;
  }


  /**
   *
   * Saves an array of bytes to a file.
   *
   * <p>If the destination exists, a confirmation dialog will appear.</p>
   *
   * @param parentComponent a component to be the parent of any error popups
   * @param path path to a file to save
   * @param bytes data to save
   * @return true if successful, false otherwise
   */
  public static boolean saveBytes(final Component parentComponent, String path, byte[] bytes) {
    UHSErrorHandler errorHandler = UHSErrorHandlerManager.getErrorHandler();
    boolean result = false;
    File destFile = new File(path);
    if (bytes == null || new File(destFile.getParent()).exists() == false) return result;

    if (destFile.exists()) {
      final String message = "A file named "+ destFile.getName() +" exists. Overwrite?";
      final int[] delayedChoice = new int[] {JOptionPane.NO_OPTION};
      Runnable r = new Runnable() {
        @Override
        public void run() {
          delayedChoice[0] = JOptionPane.showConfirmDialog(parentComponent, message, "Overwrite?", JOptionPane.YES_NO_OPTION);
        }
      };
      waitForEventPrompt(r);
      if (delayedChoice[0] == JOptionPane.NO_OPTION) return result;
    }

    BufferedOutputStream dest = null;
    try {
      int chunkSize = 1024;
      FileOutputStream fos = new FileOutputStream(path);
      dest = new BufferedOutputStream(fos, chunkSize);
      dest.write(bytes);
      dest.flush();
      dest.close();

      result = true;
    }
    catch (IOException e) {
      if (errorHandler != null) errorHandler.log(UHSErrorHandler.ERROR, null, "Could not write to "+ path, 0, e);
    }
    finally {
      try{if (dest != null) dest.close();}
      catch (Exception f) {}
    }

    return result;
  }


  /**
   * Gets the User-Agent to use for http requests.
   *
   * @return the agent text
   * @see #setUserAgent(String)
   */
  public static String getUserAgent() {
    return userAgent;
  }

  /**
   * Sets the User-Agent to use for http requests.
   *
   * <p>The default is "UHSWIN/5.2".</p>
   *
   * @param s the agent text
   * @see #getUserAgent()
   */
  public static void setUserAgent(String s) {
    if (s != null) userAgent = s;
  }


  /**
   * Gets the url of the hint catalog.
   *
   * @return the url
   * @see #setCatalogUrl(String)
   */
  public static String getCatalogUrl() {
    return catalogUrl;
  }

  /**
   * Gets the url of the hint catalog.
   *
   * <p>The default is "http://www.uhs-hints.com:80/cgi-bin/update.cgi".</p>
   *
   * @param s the url
   * @see #getCatalogUrl()
   */
  public static void setCatalogUrl(String s) {
    if (s != null) catalogUrl = s;
  }


  private static void waitForEventPrompt(final Runnable r) {
    if (EventQueue.isDispatchThread()) {
      r.run();
    } else {
      final int[] doneLock = new int[] {0};
      Runnable wrapper = new Runnable() {
        @Override
        public void run() {
          r.run();
          synchronized (doneLock) {doneLock[0] = 1;}
        }
      };

      EventQueue.invokeLater(wrapper);
      synchronized(doneLock) {
        while (doneLock[0] != 1) {
          try {doneLock.wait(500);}
          catch(InterruptedException e) {}
        }
      }
    }
  }
}
