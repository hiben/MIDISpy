/*
MIDISpy - Copyright (c) 2012 Hendrik Iben - hendrik [dot] iben <at> googlemail [dot] com
Spies on your MIDI data...

Permission is hereby granted, free of charge, to any person obtaining a copy of
this software and associated documentation files (the "Software"), to deal in
the Software without restriction, including without limitation the rights to
use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies
of the Software, and to permit persons to whom the Software is furnished to do
so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
 */

package midispy;

import java.awt.BorderLayout;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.PointerInfo;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiDevice.Info;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Receiver;
import javax.sound.midi.Transmitter;
import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

public class MIDISpy implements Runnable {
	
	private JFrame frame;

	private List<MidiDevice> inputDevices = new LinkedList<MidiDevice>();
	private List<MidiDevice> outputDevices = new LinkedList<MidiDevice>();
	
	private JComboBox oDev1;
	private JComboBox iDev1;
	private JComboBox oDev2;
	private JComboBox iDev2;
	
	private JCheckBox autoScroll;
	
	private JTextArea dataDisplay;
	
	private JButton startButton;
	private JButton stopButton;
	
	private MidiDevice currentInput1 = null;
	private MidiDevice currentOutput1 = null;
	private MidiDevice currentInput2 = null;
	private MidiDevice currentOutput2 = null;
	
	private Relay relay1 = null;
	private Relay relay2 = null;

	public static void main(String [] args) {
		EventQueue.invokeLater(new MIDISpy());
	}
	
	private void scrollView() {
		if(autoScroll.isSelected()) {
			EventQueue.invokeLater(new Runnable() {
				public void run() {
					synchronized(dataDisplay) {
						int pos = dataDisplay.getDocument().getLength();
						dataDisplay.setCaretPosition(pos);
						Point p = dataDisplay.getCaret().getMagicCaretPosition();
						if(p==null) {
							EventQueue.invokeLater(this);
						} else {
							p.x = 0;
							Rectangle r = new Rectangle();
							r.add(p);
							dataDisplay.scrollRectToVisible(r);
						}
					}
				}
			});
		}
	}
	
	private void addText(String txt) {
		synchronized (dataDisplay) {
			dataDisplay.append(txt);
		}
		scrollView();
	}
	
	private static final String acStart = "start";
	private static final String acStop = "stop";
	private static final String acClear = "clear";
	private static final String acSave = "save";
	
	private static final char [] hexchars = { 
		'0', '1', '2', '3',
		'4', '5', '6', '7',
		'8', '9', 'A', 'B',
		'C', 'D', 'E', 'F',
		};
	
	private class Relay implements Receiver {
		
		private Transmitter input;
		private Receiver output;
		private StringBuilder sb = new StringBuilder();
		private String prefix = "";
		
		public Relay(Transmitter input, String prefix, Receiver output) {
			this.input = input;
			this.prefix = prefix == null ? "" : prefix;
			this.output = output;
			input.setReceiver(this);
		}

		@Override
		public void send(MidiMessage message, long timeStamp) {
			byte [] mbytes = message.getMessage();
			sb.setLength(0);
			sb.append(prefix);
			for(byte b : mbytes) {
				if(sb.length()>0)
					sb.append(' ');
				sb.append(hexchars[(b>>4)&0xF]);
				sb.append(hexchars[b&0xF]);
			}
			sb.append('\n');
			
			addText(sb.toString());
			
			output.send(message, timeStamp);
		}

		@Override
		public void close() {
			input.close();
			output.close();
		}
	}
	
	private void cleanup() {
		if(relay1!=null)
			relay1.close();
		if(relay2!=null)
			relay2.close();

		if(currentInput1!=null)
			currentInput1.close();
		if(currentOutput1!=null)
			currentOutput1.close();
		if(currentInput2!=null)
			currentInput2.close();
		if(currentOutput2!=null)
			currentOutput2.close();

		currentInput1 = null;
		currentOutput1 = null;
		currentInput2 = null;
		currentOutput2 = null;

		relay1 = null;
		relay2 = null;
	}
	
	private JFileChooser saveChooser = null;
	
	private AbstractAction buttonAction = new AbstractAction(acStart) {
		private static final long serialVersionUID = 1L;

		@Override
		public void actionPerformed(ActionEvent e) {
			if(e.getActionCommand().equals(acStart)) {
				int seli1 = iDev1.getSelectedIndex();
				int selo1 = oDev1.getSelectedIndex();
				int seli2 = iDev2.getSelectedIndex();
				int selo2 = oDev2.getSelectedIndex();
				
				if(seli1 == -1) {
					JOptionPane.showMessageDialog(frame, "No first input device selected!", "Missing Input Device", JOptionPane.ERROR_MESSAGE);
					return;
				}
				if(selo1 == -1) {
					JOptionPane.showMessageDialog(frame, "No first output device selected!", "Missing Output Device", JOptionPane.ERROR_MESSAGE);
					return;
				}
				if(seli2 == -1) {
					JOptionPane.showMessageDialog(frame, "No second input device selected!", "Missing Input Device", JOptionPane.ERROR_MESSAGE);
					return;
				}
				if(selo2 == -1) {
					JOptionPane.showMessageDialog(frame, "No second output device selected!", "Missing Output Device", JOptionPane.ERROR_MESSAGE);
					return;
				}
				if(selo1 == selo2) {
					JOptionPane.showMessageDialog(frame, "Same device choosen for both outputs!", "Same Output Device", JOptionPane.ERROR_MESSAGE);
					return;
				}
				if(seli1 == seli2) {
					JOptionPane.showMessageDialog(frame, "Same device choosen for both inputs!", "Same Input Device", JOptionPane.ERROR_MESSAGE);
					return;
				}
				
				currentInput1 = inputDevices.get(seli1);
				currentOutput1 = outputDevices.get(selo1);
				currentInput2 = inputDevices.get(seli2);
				currentOutput2 = outputDevices.get(selo2);

				try {
					currentInput1.open();
					currentOutput1.open();
					currentInput2.open();
					currentOutput2.open();
					
					Transmitter t1 = currentInput1.getTransmitter();
					Receiver r1 = currentOutput1.getReceiver();
					Transmitter t2 = currentInput2.getTransmitter();
					Receiver r2 = currentOutput2.getReceiver();
					
					relay1 = new Relay(t1, ">>> ", r2);
					relay2 = new Relay(t2, "<<< ", r1);

					iDev1.setEnabled(false);
					oDev1.setEnabled(false);
					iDev2.setEnabled(false);
					oDev2.setEnabled(false);
					
					stopButton.setEnabled(true);
					startButton.setEnabled(false);
				} catch (MidiUnavailableException e1) {
					cleanup();
				}
			}
			if(e.getActionCommand().equals(acStop)) {
				cleanup();
				startButton.setEnabled(true);
				stopButton.setEnabled(false);

				iDev1.setEnabled(true);
				oDev1.setEnabled(true);
				iDev2.setEnabled(true);
				oDev2.setEnabled(true);
			}
			if(e.getActionCommand().equals(acClear)) {
				synchronized (dataDisplay) {
					dataDisplay.setText("");
				}
			}
			if(e.getActionCommand().equals(acSave)) {
				String text = "";
				synchronized (dataDisplay) {
					text = dataDisplay.getText();
				}
				
				if(saveChooser==null) {
					saveChooser = new JFileChooser();
					saveChooser.setSelectedFile(new File("dump.txt"));
				}
				
				saveChooser.setDialogTitle("Select file for dumps...");
				while(saveChooser.showSaveDialog(frame) == JFileChooser.APPROVE_OPTION) {
					File f = saveChooser.getSelectedFile();
					if(f.exists()) {
						int r =JOptionPane.showConfirmDialog(frame, "Overwrite existing file '" + f.getName() + "' ?", "File exists...", JOptionPane.YES_NO_CANCEL_OPTION);
						if(r == JOptionPane.NO_OPTION)
							continue;
						if(r != JOptionPane.YES_OPTION)
							break;
					}
					try {
						FileOutputStream fos = new FileOutputStream(f);
						fos.write(text.getBytes());
						fos.close();
					} catch (IOException ioe) {
						JOptionPane.showMessageDialog(frame, "An error occured while writing to the file...\n" + ioe.getMessage(), "File-Error", JOptionPane.ERROR_MESSAGE);
					}
					break;
				}
			}
		}
		
	};

	public void run() {
		
		
		Info[] devInfos = MidiSystem.getMidiDeviceInfo();
		
		for(Info i : devInfos) {
			try {
				MidiDevice md = MidiSystem.getMidiDevice(i);
				
				if(md.getMaxReceivers()!=0) {
					outputDevices.add(md);
				}
				if(md.getMaxTransmitters()!=0) {
					inputDevices.add(md);
				}
			} catch (MidiUnavailableException e) {
			}
		}
		
		String [] iDevNames = new String [inputDevices.size()];
		int index = 0;
		String prevName = null;
		int num = 2;
		for(MidiDevice md : inputDevices) {
			String name = md.getDeviceInfo().getName();
			String addName = name;
			if(prevName != null && prevName.equals(name)) {
				addName = name + " (" + num++ + ")";
			} else {
				num = 2;
			}
			prevName = name;
			iDevNames[index++] = addName;
		}
		prevName = null;
		String [] oDevNames = new String [outputDevices.size()];
		index = 0;
		for(MidiDevice md : outputDevices) {
			String name = md.getDeviceInfo().getName();
			String addName = name;
			if(prevName != null && prevName.equals(name)) {
				addName = name + " (" + num++ + ")";
			} else {
				num = 2;
			}
			prevName = name;
			oDevNames[index++] = addName;
		}
		
		PointerInfo pi = MouseInfo.getPointerInfo();
		frame = new JFrame("MIDISpy", pi.getDevice().getDefaultConfiguration());
		frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		frame.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				cleanup();
			}
		});
		
		JPanel p;
		p = new JPanel();
		p.setLayout(new GridLayout(-1, 4));
		
		p.add(new JLabel("Input 1 >>>"));
		p.add(new JLabel("Output 1 ( gets <<< )"));
		p.add(new JLabel("Input 2 <<<"));
		p.add(new JLabel("Output 2 ( gets >>> )"));
		
		p.add(iDev1 = new JComboBox(iDevNames));
		p.add(oDev1 = new JComboBox(oDevNames));

		p.add(iDev2 = new JComboBox(iDevNames));
		p.add(oDev2 = new JComboBox(oDevNames));
		
		frame.add(p, BorderLayout.NORTH);
		
		dataDisplay = new JTextArea(10, 0);
		dataDisplay.setLineWrap(false);
		dataDisplay.setEditable(false);
		
		frame.add(new JScrollPane(dataDisplay), BorderLayout.CENTER);
		
		p = new JPanel();
		p.setLayout(new FlowLayout(FlowLayout.LEFT));
		
		startButton = new JButton(buttonAction);
		startButton.setText("Start");
		startButton.setToolTipText("start transmitting data");
		startButton.setActionCommand(acStart);
		p.add(startButton);

		stopButton = new JButton(buttonAction);
		stopButton.setText("Stop");
		stopButton.setToolTipText("stop transmitting data");
		stopButton.setActionCommand(acStop);
		stopButton.setEnabled(false);
		p.add(stopButton);
		
		JButton b;
		b = new JButton(buttonAction);
		b.setText("Clear");
		b.setToolTipText("clear dumps");
		b.setActionCommand(acClear);
		p.add(b);

		b = new JButton(buttonAction);
		b.setText("Save");
		b.setToolTipText("save shown dumps to a file");
		b.setActionCommand(acSave);
		p.add(b);
		
		p.add(autoScroll = new JCheckBox("automatic scrolling", true));
			
		frame.add(p, BorderLayout.SOUTH);
		
		frame.pack();
		frame.setVisible(true);
	}

}
