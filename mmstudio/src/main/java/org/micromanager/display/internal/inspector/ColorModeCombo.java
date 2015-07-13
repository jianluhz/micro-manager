///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     Display implementation
//-----------------------------------------------------------------------------
//
// AUTHOR:       Chris Weisiger, 2015
//
// COPYRIGHT:    University of California, San Francisco, 2015
//
// LICENSE:      This file is distributed under the BSD license.
//               License text is included with the source distribution.
//
//               This file is distributed in the hope that it will be useful,
//               but WITHOUT ANY WARRANTY; without even the implied warranty
//               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.

package org.micromanager.display.internal.inspector;

import com.bulenkov.iconloader.IconLoader;

import ij.CompositeImage;
import ij.ImagePlus;
import ij.process.ImageProcessor;
import ij.process.LUT;

import net.miginfocom.swing.MigLayout;

import java.awt.Color;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;

import javax.swing.event.MouseInputAdapter;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.SwingConstants;
import javax.swing.UIManager;

import org.micromanager.data.Coords;
import org.micromanager.data.Datastore;

import org.micromanager.display.DisplaySettings;
import org.micromanager.display.DisplayWindow;
import org.micromanager.display.internal.DefaultDisplaySettings;

import org.micromanager.internal.utils.DaytimeNighttime;
import org.micromanager.internal.utils.ReportingUtils;

/**
 * This module provides a button with a dropdown menu, allowing the user to
 * select a color mode to use for displaying images -- including grayscale,
 * color, composite, and various LUT (lookup table)-based methods.
 * It behaves basically similarly to a JComboBox, hence the name, but doesn't
 * use JComboBox because we ran into issues with rendering.
 * TODO: LUTs with color displays don't currently work.
 */
public class ColorModeCombo extends JButton {
   /**
    * Provides an ImageIcon and the byte data used to generate that icon.
    */
   private static class IconWithStats {
      private static final int ICON_WIDTH = 64;
      private static final int ICON_HEIGHT = 10;

      public String text_;
      public byte[] red_;
      public byte[] green_;
      public byte[] blue_;
      public Icon icon_;

      /**
       * Derive the icon from the byte arrays we are provided.
       */
      public IconWithStats(String text, byte[] red, byte[] green, byte[] blue) {
         text_ = text;
         // Clone the arrays because they get re-used in the process of
         // creating all IconWithStats instances.
         red_ = red.clone();
         green_ = green.clone();
         blue_ = blue.clone();
         icon_ = makeLUTIcon(red, green, blue);
      }

      /**
       * Take in a pre-set icon.
       */
      public IconWithStats(String text, Icon icon, byte[] red, byte[] green,
            byte[] blue) {
         text_ = text;
         icon_ = icon;
         // Null shows up here for the color and composite options.
         if (red != null) {
            red_ = red.clone();
            green_ = green.clone();
            blue_ = blue.clone();
         }
      }

      /**
       * Generate a gradient icon for one of the LUTs we support.
       */
      public static ImageIcon makeLUTIcon(byte[] red, byte[] green,
            byte[] blue) {
         int[] pixels = new int[ICON_WIDTH * ICON_HEIGHT];
         double ratio = (double) 256 / (double) ICON_WIDTH;
         for (int y = 0; y < ICON_HEIGHT; y++) {
            for (int x = 0; x < ICON_WIDTH; x++) {
               int index = (int) (ratio * x);
               int ri = 0xff & red[index];
               int rg = 0xff & green[index];
               int rb = 0xff & blue[index];
               pixels[y * ICON_WIDTH + x] = ((0xff << 24) | (ri << 16)
                       | (rg << 8) | (rb) );
            }
         }
         BufferedImage image = new BufferedImage(ICON_WIDTH, ICON_HEIGHT,
               BufferedImage.TYPE_INT_ARGB);
         image.setRGB(0, 0, ICON_WIDTH, ICON_HEIGHT, pixels, 0, ICON_WIDTH);
         return new ImageIcon(image) {
            @Override
            public boolean equals(Object alt) {
               return alt == this;
            }
         };
      }
   }

   public static IconWithStats GRAY;
   // Highlights the dimmest and brightest pixels; otherwise gray.
   public static IconWithStats GLOW_OVER_UNDER;
   public static IconWithStats FIRE;
   public static IconWithStats REDHOT;
   public static IconWithStats SPECTRUM;
   private static Icon EMPTY = IconLoader.getIcon(
         "/org/micromanager/icons/empty.png");

   static {
      byte[] red = new byte[256];
      byte[] green = new byte[256];
      byte[] blue = new byte[256];

      // Gray
      for (int i = 0; i < 256; ++i) {
         red[i] = (byte) i;
         green[i] = (byte) i;
         blue[i] = (byte) i;
      }
      GRAY = new IconWithStats("Grayscale", red, green, blue);

      // Glow. In the icon, we make the low/high bits wider so they're more
      // visible.
      byte[] iconRed = red.clone();
      byte[] iconGreen = green.clone();
      byte[] iconBlue = blue.clone();
      for (int i = 0; i < 6; ++i) {
         iconRed[i] = (byte) 0;
         iconGreen[i] = (byte) 0;
         iconBlue[i] = (byte) 255;
         iconRed[255 - i] = (byte) 255;
         iconGreen[255 - i] = (byte) 0;
         iconBlue[255 - i] = (byte) 0;
      }
      red[0] = (byte) 0;
      green[0] = (byte) 0;
      blue[0] = (byte) 255;
      red[255] = (byte) 255;
      green[255] = (byte) 0;
      blue[255] = (byte) 0;
      GLOW_OVER_UNDER = new IconWithStats("Highlight min/max",
            IconWithStats.makeLUTIcon(iconRed, iconGreen, iconBlue),
            red, green, blue);
   }

   // Four false-color modes, grayscale, color, and composite.
   private static final ArrayList<IconWithStats> ICONS = new ArrayList<IconWithStats>();
   static {
      ICONS.add(GRAY);
      ICONS.add(new IconWithStats("Color", EMPTY, null, null, null));
      ICONS.add(new IconWithStats("Composite", EMPTY, null, null, null));
      ICONS.add(GLOW_OVER_UNDER);
   }

   private DisplayWindow display_;

   public ColorModeCombo(DisplayWindow display) {
      super(ICONS.get(0).text_, ICONS.get(0).icon_);
      display_ = display;

      setToolTipText("Set how the image display uses color");

      addMouseListener(new MouseInputAdapter() {
         @Override
         public void mousePressed(MouseEvent e) {
            showMenu();
         }
      });

      DisplaySettings settings = display_.getDisplaySettings();
      if (settings.getChannelColorMode() != null) {
         setModeByIndex(settings.getChannelColorMode().getIndex());
      }
   }

   /**
    * Generate and display the popup menu.
    */
   private void showMenu() {
      JPopupMenu menu = new JPopupMenu();

      for (int i = 0; i < ICONS.size(); ++i) {
         final int index = i;
         IconWithStats icon = ICONS.get(i);
         JMenuItem item = new JMenuItem(icon.text_, icon.icon_);
         item.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
               setModeByIndex(index);
            }
         });
         // HACK: disable the Color/Composite options for single-channel
         // displays, as ImageJ doesn't support those combinations.
         DisplaySettings.ColorMode mode = DisplaySettings.ColorMode.fromInt(i);
         if (display_.getDatastore().getAxisLength(Coords.CHANNEL) <= 1 &&
               (mode == DisplaySettings.ColorMode.COLOR ||
               mode == DisplaySettings.ColorMode.COMPOSITE)) {
            item.setEnabled(false);
         }
         menu.add(item);
      }
      menu.show(this, 0, 0);
   }

   public void setModeByIndex(int index) {
      if (index < 0 || index >= ICONS.size()) {
         ReportingUtils.logError("Invalid color mode index selection " + index);
         return;
      }
      boolean isColored = (
            index == DisplaySettings.ColorMode.COLOR.getIndex() ||
            index == DisplaySettings.ColorMode.COMPOSITE.getIndex());
      if (isColored && !(display_.getImagePlus() instanceof CompositeImage)) {
         // Non-composite ImagePlus objects don't support colored or composite
         // view modes.
         setModeByIndex(
               DisplaySettings.ColorMode.GRAYSCALE.getIndex());
         return;
      }
      if (isColored) {
         setColoredMode(index);
      }
      else {
         setLUTMode(index);
      }
   }

   /**
    * Set the color or composite mode.
    */
   private void setColoredMode(int index) {
      CompositeImage composite = (CompositeImage) (display_.getImagePlus());
      DisplaySettings.DisplaySettingsBuilder builder = display_.getDisplaySettings().copy();
      DisplaySettings.ColorMode mode = DisplaySettings.ColorMode.fromInt(index);
      if (mode == DisplaySettings.ColorMode.COMPOSITE) {
         if (display_.getDatastore().getAxisLength(Coords.CHANNEL) > 7) {
            JOptionPane.showMessageDialog(null,
               "Images with more than 7 channels cannot be displayed in Composite mode.");
            // Send them back to Color mode.
            mode = DisplaySettings.ColorMode.COLOR;
            setModeByIndex(mode.getIndex());
            return;
         }
         else {
            mode = DisplaySettings.ColorMode.COMPOSITE;
            composite.setMode(CompositeImage.COMPOSITE);
         }
      }
      else if (mode == DisplaySettings.ColorMode.COLOR) {
         composite.setMode(CompositeImage.COLOR);
         mode = DisplaySettings.ColorMode.COLOR;
      }
      else if (mode == DisplaySettings.ColorMode.GRAYSCALE) {
         composite.setMode(CompositeImage.GRAYSCALE);
         mode = DisplaySettings.ColorMode.GRAYSCALE;
      }
      else {
         ReportingUtils.showError("Unsupported color mode " + mode);
      }
      builder.channelColorMode(mode);
      composite.updateAndDraw();
      DisplaySettings settings = builder.build();
      DefaultDisplaySettings.setStandardSettings(settings);
      display_.setDisplaySettings(settings);

      setText(ICONS.get(index).text_);
      setIcon(ICONS.get(index).icon_);
   }

   /**
    * Set a LUT-based mode.
    */
   private void setLUTMode(int index) {
      ImagePlus plus = display_.getImagePlus();
      IconWithStats icon = ICONS.get(index);
      LUT lut = new LUT(8, icon.red_.length, icon.red_, icon.green_,
               icon.blue_);
      plus.getProcessor().setColorModel(lut);
      if (plus instanceof CompositeImage) {
         // Composite LUTs are done by shifting to grayscale and setting the
         // LUT for all channels to be the same; only one will be drawn at a
         // time.
         CompositeImage composite = (CompositeImage) plus;
         composite.setMode(CompositeImage.GRAYSCALE);
         for (int i = 0; i < display_.getDatastore().getAxisLength(
                  Coords.CHANNEL); ++i) {
            // ImageJ is 1-indexed...
            composite.setChannelLut(lut, i + 1);
         }
      }
      setText(icon.text_);
      setIcon(icon.icon_);
      plus.updateAndDraw();

      DisplaySettings settings = display_.getDisplaySettings().copy()
         .channelColorMode(DisplaySettings.ColorMode.fromInt(index)).build();
      display_.setDisplaySettings(settings);
   }
}