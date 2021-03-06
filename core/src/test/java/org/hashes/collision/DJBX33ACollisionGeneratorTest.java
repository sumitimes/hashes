/**
 *    Copyright 2012 Pedro Ribeiro
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.hashes.collision;

import org.testng.annotations.Test;

/**
 * DJBX33A hash collision generator test.
 * 
 * @author ribeirux
 * @version $Revision$
 */
@Test(groups = "functional", testName = "collision.DJBX33ACollisionGeneratorTest")
public class DJBX33ACollisionGeneratorTest extends CollisionGeneratorTestBase {

    /**
     * Test DJBX33A hash collision algorithm.
     */
    public void testDJBX33A() {
        this.testCollisionGenerator(new DJBX33ACollisionGenerator(), true);
    }

    /**
     * Test DJBX33A pre-built hash collisions.
     */
    public void testPreBuiltDJBX33A() {
        this.testCollisionGenerator(new DJBX33ACollisionGenerator(), false);
    }
}
