/*
 *   JTelegraph -- a Java message notification library
 *   Copyright (c) 2012, Paulo Roberto Massa Cereda
 *   All rights reserved.
 *
 *   Redistribution and use in source and binary forms, with or without
 *   modification, are permitted provided that the following conditions
 *   are met:
 *
 *   1. Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 *   2. Redistributions in binary form must reproduce the above copyright
 *   notice, this list of conditions and the following disclaimer in the
 *   documentation and/or other materials provided with the distribution.
 *
 *   3. Neither the name of the project's author nor the names of its
 *   contributors may be used to endorse or promote products derived from
 *   this software without specific prior written permission.
 *
 *   THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 *   "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 *   LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 *   FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 *   COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 *   INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 *   BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 *   LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 *   CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 *   LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY
 *   WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 *   POSSIBILITY OF SUCH DAMAGE.
 */
package org.mars_sim.msp.ui.swing.notification;

import org.pushingpixels.trident.Timeline;
import org.pushingpixels.trident.callback.TimelineCallback;

/**
 * Implements a simple callback for Timeline.
 * 
 * @author Paulo Roberto Massa Cereda
 * @version 3.1.2 2020-09-02
 * @since 2.0
 */
public class SimpleCallback implements TimelineCallback {

	/**
	 * The next timeline to display
	 */
	private final Timeline nextTimeline;

	/**
	 * @param next
	 *            The next timeline to display
	 */
	public SimpleCallback(final Timeline next) {
		nextTimeline = next;
	}

	/**
	 * @see TimelineCallback#onTimelineStateChanged(org.pushingpixels.trident.Timeline.TimelineState,
	 *      org.pushingpixels.trident.Timeline.TimelineState, float, float)
	 */
	@Override
	public void onTimelineStateChanged(final Timeline.TimelineState ts,
			final Timeline.TimelineState ts1, final float f, final float f1) {
		// If the current timeline is done then we play the next one...
		if (ts1 == Timeline.TimelineState.DONE)
			nextTimeline.play();
	}

	/**
	 * @see TimelineCallback#onTimelinePulse(float, float)
	 */
	@Override
	public void onTimelinePulse(final float f, final float f1) {
	}
}
