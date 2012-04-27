/*
 * #%L
 * ImgLib2: a general-purpose, multidimensional image processing library.
 * %%
 * Copyright (C) 2009 - 2012 Stephan Preibisch, Stephan Saalfeld, Tobias
 * Pietzsch, Albert Cardona, Barry DeZonia, Curtis Rueden, Lee Kamentsky, Larry
 * Lindsey, Johannes Schindelin, Christian Dietz, Grant Harris, Jean-Yves
 * Tinevez, Steffen Jaensch, Mark Longair, Nick Perry, and Jan Funke.
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 2 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-2.0.html>.
 * #L%
 */

package tests;

import static net.imglib2.type.numeric.ARGBType.alpha;
import static net.imglib2.type.numeric.ARGBType.blue;
import static net.imglib2.type.numeric.ARGBType.green;
import static net.imglib2.type.numeric.ARGBType.red;
import static net.imglib2.type.numeric.ARGBType.rgba;
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.gui.ImageWindow;
import ij.plugin.PlugIn;

import java.awt.Canvas;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelListener;

import net.imglib2.Cursor;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.display.ARGBScreenImage;
import net.imglib2.display.ChannelARGBConverter;
import net.imglib2.display.CompositeXYProjector;
import net.imglib2.display.XYRandomAccessibleProjector;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.interpolation.InterpolatorFactory;
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory;
import net.imglib2.interpolation.randomaccess.NearestNeighborInterpolatorFactory;
import net.imglib2.io.ImgIOException;
import net.imglib2.io.ImgOpener;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.view.Views;

/**
 * TODO
 *
 */
public abstract class AbstractInteractiveExample< T extends NumericType< T > > implements PlugIn, KeyListener, MouseWheelListener, MouseListener, MouseMotionListener
{
	final protected class GUI
	{
		final private ImageWindow window;

		final private Canvas canvas;

		final private ImageJ ij;

		/* backup */
		private KeyListener[] windowKeyListeners;

		private KeyListener[] canvasKeyListeners;

		private KeyListener[] ijKeyListeners;

		private MouseListener[] canvasMouseListeners;

		private MouseMotionListener[] canvasMouseMotionListeners;

		private MouseWheelListener[] windowMouseWheelListeners;

		GUI( final ImagePlus imp )
		{
			window = imp.getWindow();
			canvas = imp.getCanvas();

			ij = IJ.getInstance();
		}

		/**
		 * Add new event handlers.
		 */
		final void takeOverGui()
		{
			canvas.addKeyListener( AbstractInteractiveExample.this );
			window.addKeyListener( AbstractInteractiveExample.this );

			canvas.addMouseMotionListener( AbstractInteractiveExample.this );

			canvas.addMouseListener( AbstractInteractiveExample.this );

			if ( ij != null )
				ij.addKeyListener( AbstractInteractiveExample.this );

			window.addMouseWheelListener( AbstractInteractiveExample.this );
		}

		/**
		 * Backup old event handlers for restore.
		 */
		final void backupGui()
		{
			canvasKeyListeners = canvas.getKeyListeners();
			windowKeyListeners = window.getKeyListeners();
			if ( ij != null )
				ijKeyListeners = ij.getKeyListeners();
			canvasMouseListeners = canvas.getMouseListeners();
			canvasMouseMotionListeners = canvas.getMouseMotionListeners();
			windowMouseWheelListeners = window.getMouseWheelListeners();
			clearGui();
		}

		/**
		 * Restore the previously active Event handlers.
		 */
		final void restoreGui()
		{
			clearGui();
			for ( final KeyListener l : canvasKeyListeners )
				canvas.addKeyListener( l );
			for ( final KeyListener l : windowKeyListeners )
				window.addKeyListener( l );
			if ( ij != null )
				for ( final KeyListener l : ijKeyListeners )
					ij.addKeyListener( l );
			for ( final MouseListener l : canvasMouseListeners )
				canvas.addMouseListener( l );
			for ( final MouseMotionListener l : canvasMouseMotionListeners )
				canvas.addMouseMotionListener( l );
			for ( final MouseWheelListener l : windowMouseWheelListeners )
				window.addMouseWheelListener( l );
		}

		/**
		 * Remove both ours and the backed up event handlers.
		 */
		final void clearGui()
		{
			for ( final KeyListener l : canvasKeyListeners )
				canvas.removeKeyListener( l );
			for ( final KeyListener l : windowKeyListeners )
				window.removeKeyListener( l );
			if ( ij != null )
				for ( final KeyListener l : ijKeyListeners )
					ij.removeKeyListener( l );
			for ( final MouseListener l : canvasMouseListeners )
				canvas.removeMouseListener( l );
			for ( final MouseMotionListener l : canvasMouseMotionListeners )
				canvas.removeMouseMotionListener( l );
			for ( final MouseWheelListener l : windowMouseWheelListeners )
				window.removeMouseWheelListener( l );

			canvas.removeKeyListener( AbstractInteractiveExample.this );
			window.removeKeyListener( AbstractInteractiveExample.this );
			if ( ij != null )
				ij.removeKeyListener( AbstractInteractiveExample.this );
			canvas.removeMouseListener( AbstractInteractiveExample.this );
			canvas.removeMouseMotionListener( AbstractInteractiveExample.this );
			window.removeMouseWheelListener( AbstractInteractiveExample.this );
		}
	}

	final public class MappingThread extends Thread
	{
		private boolean pleaseRepaint;

		public MappingThread()
		{
			this.setName( "MappingThread" );
		}

		@Override
		public void run()
		{
			while ( !isInterrupted() )
			{
				final boolean b;
				synchronized ( this )
				{
					b = pleaseRepaint;
					pleaseRepaint = false;
				}
				if ( b )
				{
					copyState();
					projector.map();
					paintImgLib2Overlay();
					// imp.setImage( screenImage.image() );
					visualize();
					imp.updateAndDraw();
				}
				synchronized ( this )
				{
					try
					{
						if ( !pleaseRepaint )
							wait();
					}
					catch ( final InterruptedException e )
					{}
				}
			}
		}

		public void repaint()
		{
			synchronized ( this )
			{
				pleaseRepaint = true;
				notify();
			}
		}

		public void toggleInterpolation()
		{
			++interpolation;
			interpolation %= 3;
			switch ( interpolation )
			{
			case 0:
				projector = createProjector( nnFactory );
				break;
			case 1:
				projector = createProjector( nlFactory );
				break;
			case 2:
				// projector = createProjector( laFactory );
				break;
			}

		}
	}

	public AbstractInteractiveExample()
	{
		this( "src/test/java/resources/imglib2-logo-35x40.png" );
	}

	public AbstractInteractiveExample( final String overlayFilename )
	{
		imgLib2Overlay = loadImgLib2Overlay( overlayFilename );
	}

	final protected ArrayImg< ARGBType, ? > imgLib2Overlay;

	protected ArrayImg< ARGBType, ? > loadImgLib2Overlay( final String overlayFilename )
	{
		Img< UnsignedByteType > overlay;
		try
		{
			overlay = new ImgOpener().openImg( overlayFilename, new ArrayImgFactory< UnsignedByteType >(), new UnsignedByteType() );
		}
		catch ( final ImgIOException e )
		{
			return null;
		}
		final long[] dim = new long[ overlay.numDimensions() - 1 ];
		for ( int d = 0; d < dim.length; ++d )
			dim[ d ] = overlay.dimension( d );
		final ArrayImg< ARGBType, ? > argb = new ArrayImgFactory< ARGBType >().create( dim, new ARGBType() );
		final CompositeXYProjector< UnsignedByteType > projector = new CompositeXYProjector< UnsignedByteType >( overlay, argb, ChannelARGBConverter.converterListRGBA, 2 );
		projector.setComposite( true );
		projector.map();
		return argb;
	}

	protected void paintImgLib2Overlay()
	{
		if ( imgLib2Overlay != null )
		{
			final long border = 5;
			final long[] overlaySize = new long[] { imgLib2Overlay.dimension( 0 ), imgLib2Overlay.dimension( 1 ) };
			final long[] overlayMin = new long[] { screenImage.dimension( 0 ) - overlaySize[ 0 ] - border, border };
			final Cursor< ARGBType > out = Views.iterable( Views.offsetInterval( screenImage, overlayMin, overlaySize ) ).cursor();
			final Cursor< ARGBType > in = imgLib2Overlay.cursor();
			while ( out.hasNext() )
			{
				final ARGBType t = out.next();
				final int v1 = t.get();
				final int v2 = in.next().get();
				final double a = alpha( v2 ) / 255.0;
				final double a1 = 1.0 - a;
				t.set( rgba( a1 * red( v1 ) + a * red( v2 ), a1 * green( v1 ) + a * green( v2 ), a1 * blue( v1 ) + a * blue( v2 ), 255 ) );
			}
		}
	}

	abstract protected void copyState();

	abstract protected void visualize();

	final static protected String NL = System.getProperty( "line.separator" );

	protected RandomAccessibleInterval< T > img;

	protected ImagePlus imp;

	protected GUI gui;

	final protected NearestNeighborInterpolatorFactory< T > nnFactory = new NearestNeighborInterpolatorFactory< T >();

	final protected NLinearInterpolatorFactory< T > nlFactory = new NLinearInterpolatorFactory< T >();

	// final protected LanczosInterpolatorFactory< T > laFactory = new
	// LanczosInterpolatorFactory< T >( 2, true );
	protected ARGBScreenImage screenImage;

	protected XYRandomAccessibleProjector< T, ARGBType > projector;

	/* coordinates where mouse dragging started and the drag distance */
	protected double oX, oY, dX, dY;

	protected int interpolation = 0;

	protected MappingThread painter;

	abstract protected XYRandomAccessibleProjector< T, ARGBType > createProjector( final InterpolatorFactory< T, RandomAccessible< T > > interpolatorFactory );

	abstract protected void update();

	final protected float keyModfiedSpeed( final int modifiers )
	{
		if ( ( modifiers & KeyEvent.SHIFT_DOWN_MASK ) != 0 )
			return 10;
		else if ( ( modifiers & KeyEvent.CTRL_DOWN_MASK ) != 0 )
			return 0.1f;
		else
			return 1;
	}

	@Override
	public void keyReleased( final KeyEvent e )
	{
		if ( e.getKeyCode() == KeyEvent.VK_SHIFT )
		{
			oX += 9 * dX;
			oY += 9 * dY;
		}
		else if ( e.getKeyCode() == KeyEvent.VK_CONTROL )
		{
			oX -= 9 * dX / 10;
			oY -= 9 * dY / 10;
		}
	}

	@Override
	public void keyTyped( final KeyEvent e )
	{}

	@Override
	public void mouseMoved( final MouseEvent e )
	{}

	@Override
	public void mouseClicked( final MouseEvent e )
	{}

	@Override
	public void mouseEntered( final MouseEvent e )
	{}

	@Override
	public void mouseExited( final MouseEvent e )
	{}

	@Override
	public void mouseReleased( final MouseEvent e )
	{}
}
