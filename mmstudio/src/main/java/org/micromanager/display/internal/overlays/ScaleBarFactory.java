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

package org.micromanager.display.internal.overlays;

import org.micromanager.display.OverlayPanel;
import org.micromanager.display.OverlayPanelFactory;
import org.micromanager.Studio;

public final class ScaleBarFactory implements OverlayPanelFactory {
   private Studio studio_;
   public ScaleBarFactory(Studio studio) {
      studio_ = studio;
   }

   @Override
   public OverlayPanel createOverlayPanel() {
      return new ScaleBarPanel(studio_);
   }
}