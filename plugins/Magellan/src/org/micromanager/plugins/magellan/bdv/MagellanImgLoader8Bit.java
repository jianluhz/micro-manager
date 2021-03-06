///////////////////////////////////////////////////////////////////////////////
// AUTHOR:       Henry Pinkard, henry.pinkard@gmail.com
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
//
package org.micromanager.plugins.magellan.bdv;

import org.micromanager.plugins.magellan.acq.MultiResMultipageTiffStorage;
import bdv.spimdata.legacy.LegacyViewerImgLoaderWrapper;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.volatiles.VolatileUnsignedByteType;

public class MagellanImgLoader8Bit extends LegacyViewerImgLoaderWrapper< UnsignedByteType, VolatileUnsignedByteType, LegacyMagellanImgLoader8Bit > {

   public MagellanImgLoader8Bit(MultiResMultipageTiffStorage storage) {
      super(new LegacyMagellanImgLoader8Bit(storage ));
   }
}
