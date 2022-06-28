/*
 * Copyright 2019-2022 The Polypheny Project
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

package org.polypheny.db.adapter.excel;

public class ExcelReader {
//    public static void main(String[] args)
//    {
//        try
//        {
//            File file = new File("C:\\Desktop\\employee.xlsx");   //creating a new file instance
//            FileInputStream fs = new FileInputStream(file);   //obtaining bytes from the file
//
//            //creating Workbook instance that refers to .xlsx file
//            XSSFWorkbook wb = new XSSFWorkbook(fs);
//            XSSFSheet sheet = wb.getSheetAt(0);     //creating a Sheet object to retrieve object
//            Iterator<Row> itr = sheet.iterator();    //iterating over excel file
//            while (itr.hasNext())
//            {
//                Row row = itr.next();
//                Iterator<Cell> cellIterator = row.cellIterator();   //iterating over each column
//                while (cellIterator.hasNext())
//                {
//                    Cell cell = cellIterator.next();
//                    switch (cell.getCellType())
//                    {
//                        case STRING:    //field that represents string cell type
//                            System.out.print(cell.getStringCellValue() + "\t\t\t");
//                            break;
//                        case NUMERIC:    //field that represents number cell type
//                            System.out.print(cell.getNumericCellValue() + "\t\t\t");
//                            break;
//                        default:
//                    }
//                }
//                System.out.println("");
//            }
//        }
//        catch(Exception e)
//        {
//            e.printStackTrace();
//        }
//    }
}
