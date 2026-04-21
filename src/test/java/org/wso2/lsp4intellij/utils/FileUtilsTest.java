/*
 * Copyright (c) 2019, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wso2.lsp4intellij.utils;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.BinaryLightVirtualFile;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;

import static org.mockito.Mockito.*;

public class FileUtilsTest {

    @Before
    public void setUp() {
        // Reset providers before each test
        FileUtils.resetOSProvider();
        FileUtils.resetLocalFileSystemProvider();
    }

    @After
    public void tearDown() {
        // Ensure providers are reset after each test
        FileUtils.resetOSProvider();
        FileUtils.resetLocalFileSystemProvider();
    }

    @Test
    public void testEditorFromVirtualFile() {
        VirtualFile file = mock(VirtualFile.class);
        Project project = mock(Project.class);

        Editor editor = mock(Editor.class);
        TextEditor textEditor = mock(TextEditor.class);
        when(textEditor.getEditor()).thenReturn(editor);

        FileEditorManagerEx fileEditorManagerEx = mock(FileEditorManagerEx.class);
        
        try (MockedStatic<FileEditorManager> mockedStatic = mockStatic(FileEditorManager.class)) {
            when(fileEditorManagerEx.getAllEditors(file))
                    .thenReturn(new FileEditor[]{textEditor})
                    .thenReturn(new FileEditor[0]);
            mockedStatic.when(() -> FileEditorManager.getInstance(project)).thenReturn(fileEditorManagerEx);

            Assert.assertEquals(editor, FileUtils.editorFromVirtualFile(file, project));
            Assert.assertNull(FileUtils.editorFromVirtualFile(file, project));
        }
    }

    @Test
    public void testVirtualFileFromURI() {
        // Set Unix OS for predictable behavior
        FileUtils.setOSProvider(() -> FileUtils.OS.UNIX);
        
        // Mock the LocalFileSystem provider
        VirtualFile expectedFile = new BinaryLightVirtualFile("testFile");
        FileUtils.setLocalFileSystemProvider(file -> expectedFile);

        VirtualFile result = FileUtils.virtualFileFromURI("file:///foobar");
        Assert.assertNotNull(result);
        Assert.assertEquals(expectedFile.toString(), result.toString());
    }

    @Test
    public void testVirtualFileFromURIInvalidUri() {
        // Set Unix OS for predictable behavior
        FileUtils.setOSProvider(() -> FileUtils.OS.UNIX);
        
        // Mock provider that returns null (simulating file not found)
        FileUtils.setLocalFileSystemProvider(file -> null);
        
        // For URIs that don't exist, the method returns null
        VirtualFile result = FileUtils.virtualFileFromURI("file:///nonexistent/path");
        Assert.assertNull(result);
    }

    @Test
    public void testvfsToUri() {
        VirtualFile virtualFile = mock(VirtualFile.class);
        when(virtualFile.getPath()).thenReturn("/fooBar");
        
        // Set Unix OS for predictable behavior
        FileUtils.setOSProvider(() -> FileUtils.OS.UNIX);
        
        String result = FileUtils.vfsToUri(virtualFile);
        Assert.assertNotNull(result);
        Assert.assertTrue(result.startsWith("file:///"));
    }

    @Test
    public void testvfsToUriNull() {
        Assert.assertNull(FileUtils.vfsToUri(null));
    }

    @Test
    public void testSanitizeURINull() {
        Assert.assertNull(FileUtils.sanitizeURI(null));
    }

    @Test
    public void testSanitizeURINonFileUri() {
        // Non-file URIs should be returned as-is
        Assert.assertEquals("fooBar", FileUtils.sanitizeURI("fooBar"));
    }

    @Test
    public void testSanitizeURIUnix() {
        // Set Unix OS
        FileUtils.setOSProvider(() -> FileUtils.OS.UNIX);

        Assert.assertEquals("file:///fooBar", FileUtils.sanitizeURI("file://fooBar"));
        Assert.assertEquals("file:///path/to/file", FileUtils.sanitizeURI("file:///path/to/file"));
    }

    @Test
    public void testSanitizeURIWindows() {
        // Set Windows OS
        FileUtils.setOSProvider(() -> FileUtils.OS.WINDOWS);
        
        String result = FileUtils.sanitizeURI("file:///C:/path/to/file");
        Assert.assertNotNull(result);
        Assert.assertTrue(result.startsWith("file:///"));
    }

    @Test
    public void testuriToVfs() {
        // Set Unix OS for predictable behavior
        FileUtils.setOSProvider(() -> FileUtils.OS.UNIX);
        
        // Mock the LocalFileSystem provider
        VirtualFile expectedFile = new BinaryLightVirtualFile("testFile");
        FileUtils.setLocalFileSystemProvider(file -> expectedFile);

        VirtualFile result = FileUtils.uriToVfs("file:///foobar");
        Assert.assertNotNull(result);
        Assert.assertEquals(expectedFile.toString(), result.toString());
    }

    @Test
    public void testuriToVfsInvalidUri() {
        // Set Unix OS for predictable behavior
        FileUtils.setOSProvider(() -> FileUtils.OS.UNIX);
        
        // Mock provider that returns null (simulating file not found)
        FileUtils.setLocalFileSystemProvider(file -> null);
        
        // For URIs that don't exist, the method returns null
        VirtualFile result = FileUtils.uriToVfs("file:///nonexistent/path");
        Assert.assertNull(result);
    }

    @Test
    public void testEditorToProjectFolderPathNull() {
        Assert.assertNull(FileUtils.editorToProjectFolderPath(null));
    }

    @Test
    public void testEditorToProjectFolderPathNoProject() {
        Editor editor = mock(Editor.class);
        when(editor.getProject()).thenReturn(null);
        Assert.assertNull(FileUtils.editorToProjectFolderPath(editor));
    }

    @Test
    public void testEditorToProjectFolderPathNoBasePath() {
        Editor editor = mock(Editor.class);
        Project project = mock(Project.class);
        when(editor.getProject()).thenReturn(project);
        when(project.getBasePath()).thenReturn(null);
        Assert.assertNull(FileUtils.editorToProjectFolderPath(editor));
    }

    @Test
    public void testEditorToProjectFolderPath() {
        Editor editor = mock(Editor.class);
        Project project = mock(Project.class);
        when(editor.getProject()).thenReturn(project);
        when(project.getBasePath()).thenReturn("/test/path");

        String result = FileUtils.editorToProjectFolderPath(editor);
        Assert.assertNotNull(result);
        Assert.assertTrue(result.contains("test"));
    }

    @Test
    public void testProjectToUriNull() {
        Assert.assertNull(FileUtils.projectToUri(null));
    }

    @Test
    public void testProjectToUriNoBasePath() {
        Project project = mock(Project.class);
        when(project.getBasePath()).thenReturn(null);
        Assert.assertNull(FileUtils.projectToUri(project));
    }

    @Test
    public void testProjectToUri() {
        // Set Unix OS for predictable behavior
        FileUtils.setOSProvider(() -> FileUtils.OS.UNIX);
        
        Project project = mock(Project.class);
        when(project.getBasePath()).thenReturn("/test/path");

        String result = FileUtils.projectToUri(project);
        Assert.assertNotNull(result);
        Assert.assertTrue(result.startsWith("file:///"));
    }

    @Test
    public void testGetOS() {
        // Test that getOS returns UNIX when set
        FileUtils.setOSProvider(() -> FileUtils.OS.UNIX);
        Assert.assertEquals(FileUtils.OS.UNIX, FileUtils.getOS());

        // Test that getOS returns WINDOWS when set
        FileUtils.setOSProvider(() -> FileUtils.OS.WINDOWS);
        Assert.assertEquals(FileUtils.OS.WINDOWS, FileUtils.getOS());
    }

    @Test
    public void testResetOSProvider() {
        // Set a custom provider
        FileUtils.setOSProvider(() -> FileUtils.OS.WINDOWS);
        
        // Reset and verify it returns to default (based on actual system)
        FileUtils.resetOSProvider();
        Assert.assertNotNull(FileUtils.getOS());
    }

    @Test
    public void testResetLocalFileSystemProvider() {
        // Set a custom provider
        FileUtils.setLocalFileSystemProvider(file -> null);
        
        // Reset - just verify no exception is thrown
        FileUtils.resetLocalFileSystemProvider();
    }

    @Test
    public void testIsFileSupported() {
        VirtualFile virtualFile1 = mock(VirtualFile.class);
        when(virtualFile1.getUrl()).thenReturn("jar:");

        VirtualFile virtualFile2 = mock(VirtualFile.class);
        when(virtualFile2.getUrl()).thenReturn("");

        Assert.assertFalse(FileUtils.isFileSupported(null));
        Assert.assertFalse(FileUtils.isFileSupported(new BinaryLightVirtualFile("testFile")));
        Assert.assertFalse(FileUtils.isFileSupported(virtualFile1));
        Assert.assertFalse(FileUtils.isFileSupported(virtualFile2));
    }

    @Test
    public void testIsEditorSupported() {
        VirtualFile virtualFile1 = mock(VirtualFile.class);
        when(virtualFile1.getUrl()).thenReturn("jar:");

        try (MockedStatic<FileDocumentManager> mockedStatic = mockStatic(FileDocumentManager.class)) {
            FileDocumentManager fileDocumentManager = mock(FileDocumentManager.class);
            when(fileDocumentManager.getFile(any(Document.class))).thenReturn(virtualFile1);
            mockedStatic.when(FileDocumentManager::getInstance).thenReturn(fileDocumentManager);

            Assert.assertFalse(FileUtils.isEditorSupported(mock(Editor.class)));
        }
    }

    @Test
    public void testLowercaseWindowsDriveAndEscapeColon() {
        // Test non-Windows paths remain unchanged
        Assert.assertEquals("file:///home/user/file.txt",
                FileUtils.lowercaseWindowsDriveAndEscapeColon("file:///home/user/file.txt"));
        
        // Test Windows paths get lowercase drive and escaped colon
        Assert.assertEquals("file:///c%3A/Users/file.txt",
                FileUtils.lowercaseWindowsDriveAndEscapeColon("file:///C:/Users/file.txt"));
        
        Assert.assertEquals("file:///d%3A/path/file.txt",
                FileUtils.lowercaseWindowsDriveAndEscapeColon("file:///D:/path/file.txt"));
    }

    @Test
    public void testStartsWithWindowsDrive() {
        Assert.assertTrue(FileUtils.startsWithWindowsDrive("C:/path"));
        Assert.assertTrue(FileUtils.startsWithWindowsDrive("D:/path"));
        Assert.assertFalse(FileUtils.startsWithWindowsDrive("/unix/path"));
        Assert.assertFalse(FileUtils.startsWithWindowsDrive("relative/path"));
    }
}
