using System.Windows.Forms;

namespace BtTakeover.UI;

partial class MainForm
{
    /// <summary>
    ///  Required designer variable.
    /// </summary>
    private System.ComponentModel.IContainer components = null;

    /// <summary>
    ///  Clean up any resources being used.
    /// </summary>
    /// <param name="disposing">true if managed resources should be disposed; otherwise, false.</param>
    protected override void Dispose(bool disposing)
    {
        if (disposing && (components != null))
        {
            components.Dispose();
        }
        base.Dispose(disposing);
    }

    #region Windows Form Designer generated code

    /// <summary>
    ///  Required method for Designer support - do not modify
    ///  the contents of this method with the code editor.
    /// </summary>
    private void InitializeComponent()
    {
        label1 = new Label();
        txtDeviceId = new TextBox();
        btnScanPair = new Button();
        label2 = new Label();
        txtWavPath = new TextBox();
        btnBrowse = new Button();
        btnPlay = new Button();
        chkLoop = new CheckBox();
        txtLog = new TextBox();
        SuspendLayout();
        // 
        // label1
        // 
        label1.AutoSize = true;
        label1.Location = new System.Drawing.Point(12, 15);
        label1.Name = "label1";
        label1.Size = new System.Drawing.Size(120, 15);
        label1.TabIndex = 0;
        label1.Text = "Device ID or MAC:";
        // 
        // txtDeviceId
        // 
        txtDeviceId.Anchor = AnchorStyles.Top | AnchorStyles.Left | AnchorStyles.Right;
        txtDeviceId.Location = new System.Drawing.Point(138, 12);
        txtDeviceId.Name = "txtDeviceId";
        txtDeviceId.PlaceholderText = "e.g. 00:11:22:33:44:55 or name";
        txtDeviceId.Size = new System.Drawing.Size(472, 23);
        txtDeviceId.TabIndex = 1;
        // 
        // btnScanPair
        // 
        btnScanPair.Anchor = AnchorStyles.Top | AnchorStyles.Right;
        btnScanPair.Location = new System.Drawing.Point(616, 12);
        btnScanPair.Name = "btnScanPair";
        btnScanPair.Size = new System.Drawing.Size(120, 23);
        btnScanPair.TabIndex = 2;
        btnScanPair.Text = "Scan && Pair";
        btnScanPair.UseVisualStyleBackColor = true;
        btnScanPair.Click += btnScanPair_Click;
        // 
        // label2
        // 
        label2.AutoSize = true;
        label2.Location = new System.Drawing.Point(12, 50);
        label2.Name = "label2";
        label2.Size = new System.Drawing.Size(55, 15);
        label2.TabIndex = 3;
        label2.Text = "WAV file:";
        // 
        // txtWavPath
        // 
        txtWavPath.Anchor = AnchorStyles.Top | AnchorStyles.Left | AnchorStyles.Right;
        txtWavPath.Location = new System.Drawing.Point(138, 47);
        txtWavPath.Name = "txtWavPath";
        txtWavPath.Size = new System.Drawing.Size(472, 23);
        txtWavPath.TabIndex = 4;
        // 
        // btnBrowse
        // 
        btnBrowse.Anchor = AnchorStyles.Top | AnchorStyles.Right;
        btnBrowse.Location = new System.Drawing.Point(616, 47);
        btnBrowse.Name = "btnBrowse";
        btnBrowse.Size = new System.Drawing.Size(120, 23);
        btnBrowse.TabIndex = 5;
        btnBrowse.Text = "Browse";
        btnBrowse.UseVisualStyleBackColor = true;
        btnBrowse.Click += btnBrowse_Click;
        // 
        // btnPlay
        // 
        btnPlay.Anchor = AnchorStyles.Top | AnchorStyles.Right;
        btnPlay.Location = new System.Drawing.Point(616, 82);
        btnPlay.Name = "btnPlay";
        btnPlay.Size = new System.Drawing.Size(120, 27);
        btnPlay.TabIndex = 6;
        btnPlay.Text = "Play (Max Volume)";
        btnPlay.UseVisualStyleBackColor = true;
        btnPlay.Click += btnPlay_Click;
        // 
        // chkLoop
        // 
        chkLoop.Anchor = AnchorStyles.Top | AnchorStyles.Right;
        chkLoop.AutoSize = true;
        chkLoop.Location = new System.Drawing.Point(538, 86);
        chkLoop.Name = "chkLoop";
        chkLoop.Size = new System.Drawing.Size(54, 19);
        chkLoop.TabIndex = 8;
        chkLoop.Text = "Loop";
        chkLoop.UseVisualStyleBackColor = true;
        // 
        // txtLog
        // 
        txtLog.Anchor = AnchorStyles.Top | AnchorStyles.Bottom | AnchorStyles.Left | AnchorStyles.Right;
        txtLog.Location = new System.Drawing.Point(12, 122);
        txtLog.Multiline = true;
        txtLog.Name = "txtLog";
        txtLog.ReadOnly = true;
        txtLog.ScrollBars = ScrollBars.Vertical;
        txtLog.Size = new System.Drawing.Size(724, 226);
        txtLog.TabIndex = 7;
        // 
        // MainForm
        // 
        AutoScaleDimensions = new System.Drawing.SizeF(7F, 15F);
        AutoScaleMode = AutoScaleMode.Font;
        ClientSize = new System.Drawing.Size(748, 360);
        Controls.Add(txtLog);
        Controls.Add(chkLoop);
        Controls.Add(btnPlay);
        Controls.Add(btnBrowse);
        Controls.Add(txtWavPath);
        Controls.Add(label2);
        Controls.Add(btnScanPair);
        Controls.Add(txtDeviceId);
        Controls.Add(label1);
        MinimumSize = new System.Drawing.Size(600, 300);
        Name = "MainForm";
        Text = "BT Takeover";
        ResumeLayout(false);
        PerformLayout();
    }

    #endregion

    private Label label1;
    private TextBox txtDeviceId;
    private Button btnScanPair;
    private Label label2;
    private TextBox txtWavPath;
    private Button btnBrowse;
    private Button btnPlay;
    private TextBox txtLog;
    private CheckBox chkLoop;
}
