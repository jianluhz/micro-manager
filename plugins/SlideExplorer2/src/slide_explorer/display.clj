(ns slide-explorer.display
  (:import (java.awt Color Graphics Graphics2D RenderingHints Window)
           (java.awt.event ComponentAdapter KeyAdapter KeyEvent MouseAdapter
                           WindowAdapter)
           (java.awt.image BufferedImage WritableRaster)
           (java.awt.geom AffineTransform Point2D$Double)
           (java.io ByteArrayInputStream)
           (java.util UUID)
           (java.util.prefs Preferences)
           (javax.imageio ImageIO)
           (ij ImagePlus)
           (ij.process ImageProcessor)
           (mmcorej TaggedImage)
           (org.micromanager AcqEngine MMStudioMainFrame)
           (org.micromanager.utils GUIUpdater ImageUtils JavaUtils MDUtils)
           (org.micromanager.acquisition TaggedImageQueue))
  (:use [org.micromanager.mm :only (core edt mmc load-mm json-to-data)]
        [slide-explorer.image :only (merge-and-scale)]
        [slide-explorer.view :only (floor-int show)]))


(load-mm)

;; hardware communications

(def gui-prefs (Preferences/userNodeForPackage MMStudioMainFrame))

(defn set-stage-to-pixel-transform [^AffineTransform affine-transform]
  (JavaUtils/putObjectInPrefs
    gui-prefs (str "affine_transform_" (core getCurrentPixelSizeConfig))
    affine-transform))

(defn get-stage-to-pixel-transform []
  (JavaUtils/getObjectFromPrefs
    gui-prefs (str "affine_transform_" (core getCurrentPixelSizeConfig))
    nil))

(def angle (atom 0))

(def grab-tagged-image
  "Grab a single image from camera."
  (memoize (fn []
             (core snapImage)
             (core getTaggedImage))))

(def pixel-size (core getPixelSizeUm true))

(defn tagged-image-sequence []
  (let [q (.runSilent (AcqEngine.))]
    (take-while #(not= % TaggedImageQueue/POISON)
                (repeatedly #(.take q)))))

(defn tagged-image-to-processor [tagged-image]
  {:proc (ImageUtils/makeProcessor tagged-image)
   :tags (json-to-data (.tags tagged-image))})

(defn processor-sequence []
  (map tagged-image-to-processor (tagged-image-sequence)))

(defn get-channel-index [raw]
  (-> raw :tags (get "ChannelIndex")))

(defn get-frame-index [raw]
  (-> raw :tags (get "FrameIndex")))

(defn get-slice-index [raw]
  (-> raw :tags (get "SliceIndex")))

  
;; image properties

(defn get-image-width [^TaggedImage image]
  (MDUtils/getWidth (.tags image)))
  
(defn get-image-height [^TaggedImage image]
  (MDUtils/getWidth (.tags image)))

(defn tile-dimensions [^TaggedImage image]
  (let [w (get-image-width image)
        h (get-image-height image)]
    [(long (* 3/4 w)) (long (* 3/4 h))]))

(defn image-data [^TaggedImage tagged-image]
  {:pixels (.pix tagged-image)
   :tags (json-to-data (.tags tagged-image))})

(defn get-image-dimensions [^TaggedImage tagged-image]
  [(MDUtils/getWidth (.tags tagged-image))
   (MDUtils/getHeight (.tags tagged-image))])

;; pixels/stage

(defn pixels-to-stage [^AffineTransform pixel-to-stage-transform [x y]]
  (let [p (.transform pixel-to-stage-transform (Point2D$Double. x y) nil)]
    [(.x p) (.y p)]))
    
(defn stage-to-pixels [^AffineTransform pixel-to-stage-transform [x y]]
  (let [p (.inverseTransform pixel-to-stage-transform (Point2D$Double. x y) nil)]
    [(.x p) (.y p)]))
              
;; tile image handling

(defn awt-image [^TaggedImage tagged-image]
  (.createImage (ImageUtils/makeProcessor tagged-image)))

(defn get-tile [{:keys [nx ny nz nt nc]}]
  (ImageUtils/makeProcessor (grab-tagged-image)))
  ;(slide-explorer.image/try-3-colors false))

(defn add-tile [tile-map tile-zoom indices tile]
  (assoc-in tile-map [tile-zoom indices] tile))

(defn propagate-tiles [available-tiles zoom {:keys [nx ny nz nt nc] :as indices}]
  (when-let [parent-layer (available-tiles (inc zoom))]
    (let [nx- (* 2 nx)
          ny- (* 2 ny)
          nx+ (inc nx-)
          ny+ (inc ny-)
          a (parent-layer (assoc indices :nx nx- :ny ny-))
          b (parent-layer (assoc indices :nx nx+ :ny ny-))
          c (parent-layer (assoc indices :nx nx- :ny ny+))
          d (parent-layer (assoc indices :nx nx+ :ny ny+))]
      (add-tile available-tiles zoom
                (assoc indices :nx nx :ny ny)
                (merge-and-scale a b c d)))))

(defn child-index [n]
  (floor-int (/ n 2)))

(defn child-indices [indices]
  (-> indices
     (update-in [:nx] child-index)
     (update-in [:ny] child-index)))

(defn add-and-propagate-tiles [tile-map indices tile min-zoom]
  (loop [tile-map (add-tile tile-map 0 indices tile)
         new-indices (child-indices indices)
         zoom -1]
    (if (<= min-zoom zoom)
      (recur (propagate-tiles tile-map zoom new-indices)
             (child-indices new-indices)
             (dec zoom))
      tile-map)))

(defn add-to-available-tiles [available-tiles zoom indices]
  (send available-tiles add-and-propagate-tiles indices (get-tile indices) -8))

;; tests

(defn start []
  (let [available-tiles (agent {})]
    (def at available-tiles)
    (show available-tiles)))

;(defn set-rgb-luts []
;  (swap! ss assoc :luts rgb))

(defn test-tile [nx ny]
  (add-to-available-tiles at 0 {:nx nx
                                :ny ny
                                :nz 0 
                                :nt 0
                                :nc 0}))

(defn test-tiles [nx ny]
  (.start (Thread.
            #(doseq [i (range (- nx) (inc nx)) j (range (- ny) (inc ny))]
               ;(Thread/sleep 1000)
               (test-tile i j)))))

(defn test-rotate []
  (.start (Thread. #(do (dorun (repeatedly 2000 (fn [] (Thread/sleep 10)
                                                  (swap! angle + 0.02))))
                        (reset! angle 0)))))



