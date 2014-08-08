(ns anon-files.inputs
  (:use [clj-jargon.init]
        [clj-jargon.item-ops]
        [clj-jargon.item-info]
        [clj-jargon.paging])
  (:import [java.io InputStream]))

(def default-chunk-size 4000000)

(defn get-chunk-size
  [file-size]
  (loop [fsize file-size
         csize default-chunk-size]
    (if (< fsize csize)
      fsize
      csize)))

(defn get-num-chunks
  [chunk-size file-size]
  (int (Math/floor (/ file-size chunk-size))))

(defn get-remainder-bytes
  [chunk-size file-size]
  (mod file-size chunk-size))

(defn drop-bytes
  [istream num-bytes]
  (let [fsize      (if-not (pos? num-bytes) 1 num-bytes)
        csize      (get-chunk-size fsize)
        num-chunks (get-num-chunks csize fsize)
        remainder  (get-remainder-bytes csize fsize)]
    (let [buf (byte-array csize)]
      (doseq [iter (range num-chunks)]
        (println "Dropping chunk " iter)
        (.read istream buf 0 csize)))
    (if (pos? remainder)
      (let [buf (byte-array remainder)]
        (.read istream buf 0 remainder)))
    istream))

(defn chunk-stream
  [cm filepath start-byte end-byte]
  (let [raf (.instanceIRODSRandomAccessFile (:fileFactory cm) filepath)
        location (atom start-byte)]
    (.seek raf start-byte SEEK-CURRENT)
    (proxy [java.io.InputStream] []
      (available [] (.length raf))
      (mark [readlimit] nil)
      (markSupported [] nil)
      (read
        ([]
           (let [new-loc (inc @location)]
             (if (<= new-loc end-byte)
               (let [bufsize 1
                     buf     (byte-array bufsize)]
                 (.read raf buf 0 bufsize)
                 (reset! location new-loc)
                 (first buf))
               -1)))
        ([b]
           (if (<= @location end-byte)
             (let [diff       (inc (- end-byte @location))
                   len        (if (> (count b) diff)
                                diff
                                (count b))
                   bytes-read (.read raf b 0 len)]
               (reset! location (+ @location bytes-read))
               bytes-read)
             -1))
        ([b off len]
           (if (<= @location end-byte)
             (let [diff (inc (- end-byte @location))
                   len (if (> len diff)
                         diff
                         len)
                   bytes-read (.read raf b off len)]
               (reset! location (+ @location bytes-read))
               bytes-read)
             -1)))
      (reset [] (.seek raf 0 SEEK-START))
      (skip [n] (.skipBytes raf n))
      (close []
        (.close raf)))))
